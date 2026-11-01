package ar.felaufernandezbottaro.vinculovial.presentation.sent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetSentResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetSentUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlert
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * La lista de Enviados (`GET /alerts/sent`, paginación infinita por cursor) y el chat del emisor
 * de la alerta seleccionada. No guarda nada en el dispositivo: todo sale del servidor, y el chat
 * se reconstruye desde el estado de la alerta.
 *
 * No carga al crearse: la primera carga la pide quien muestra la pestaña ([refresh]), así que
 * entrar al buzón sin abrir Enviados no gasta el cupo de la ruta.
 */
@HiltViewModel
class SentViewModel @Inject constructor(
    private val getSent: GetSentUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SentUiState>(SentUiState.Loading)
    val uiState: StateFlow<SentUiState> = _uiState.asStateFlow()

    private val _chat = MutableStateFlow<SentAlert?>(null)

    /** La alerta enviada cuyo chat está abierto, o `null` si no hay ninguno. */
    val chat: StateFlow<SentAlert?> = _chat.asStateFlow()

    private val _events = Channel<SentEvent>(Channel.BUFFERED)
    val events: Flow<SentEvent> = _events.receiveAsFlow()

    private var nextCursor: String? = null
    private var firstPageJob: Job? = null

    /** Barrera contra pedir la misma página dos veces con el mismo cursor (scroll rápido). */
    private var pageRequestInFlight = false

    /**
     * Pide la primera página. Si ya hay una en vuelo no lanza otra y devuelve esa. Con lista
     * ya cargada es silencioso (no vuelve a mostrar el indicador de carga ni vacía la pantalla):
     * si falla, la lista que había se conserva.
     */
    fun refresh(): Job {
        firstPageJob?.takeIf { it.isActive }?.let { return it }

        val job = viewModelScope.launch {
            val hadContent = currentItems() != null
            if (!hadContent) _uiState.update { SentUiState.Loading }

            when (val result = getSent()) {
                is GetSentResult.Success -> onFirstPageLoaded(result.page)
                is GetSentResult.Failed -> if (!hadContent) _uiState.update { SentUiState.Error(result.error) }
            }
        }
        firstPageJob = job
        return job
    }

    private fun onFirstPageLoaded(page: SentPage) {
        nextCursor = page.nextCursor
        _uiState.update {
            if (page.items.isEmpty()) SentUiState.Empty else SentUiState.Content(page.items, page.nextCursor)
        }
    }

    /** Disparado por el scroll cerca del final; ignorado si ya hay una página en vuelo o no hay más. */
    fun loadNextPage() {
        if (nextCursor == null || currentItems() == null || pageRequestInFlight) return

        _uiState.update { state -> if (state is SentUiState.Content) state.copy(isLoadingMore = true) else state }

        viewModelScope.launch { loadNextPageAndWait() }
    }

    /** Reintenta la misma página que falló, con el mismo cursor: no se perdió al fallar. */
    fun retryNextPage() = loadNextPage()

    private suspend fun loadNextPageAndWait() {
        val cursor = nextCursor ?: return
        if (pageRequestInFlight) return
        pageRequestInFlight = true

        when (val result = getSent(cursor)) {
            is GetSentResult.Success -> {
                nextCursor = result.page.nextCursor
                // Los ítems vigentes al terminar, no los de antes de pedir.
                val loaded = currentItems().orEmpty()
                _uiState.update { SentUiState.Content(loaded + result.page.items, result.page.nextCursor) }
            }
            is GetSentResult.Failed ->
                _uiState.update { SentUiState.ContentWithPageError(currentItems().orEmpty(), result.error) }
        }
        pageRequestInFlight = false
    }

    // -- Chat del emisor ---------------------------------------------------------------------

    /** Abre el chat de [alertId] si está en la lista cargada. */
    fun openChat(alertId: String) {
        _chat.value = findInLoaded(alertId) ?: return
    }

    fun closeChat() {
        _chat.value = null
    }

    /**
     * Abre el chat de [alertId] a partir de un push `SENT_UPDATED`. La lista se vuelve a pedir
     * (el estado cambió, justamente) y, si el id no está en la primera página, se sigue paginando
     * hasta [MAX_LOOKUP_PAGES] páginas. Si no aparece, no abre nada: el usuario queda en la lista.
     */
    fun openChatFromPush(alertId: String) {
        viewModelScope.launch {
            refresh().join()

            var pagesRead = 1
            while (findInLoaded(alertId) == null && nextCursor != null && pagesRead < MAX_LOOKUP_PAGES) {
                val before = nextCursor
                loadNextPageAndWait()
                // Una página que falló no avanza el cursor: se corta en vez de pedirla otra vez.
                if (nextCursor == before) break
                pagesRead++
            }

            val alert = findInLoaded(alertId) ?: return@launch
            _chat.value = alert
            _events.send(SentEvent.OpenChat)
        }
    }

    private fun findInLoaded(alertId: String): SentAlert? = currentItems()?.firstOrNull { it.id == alertId }

    private fun currentItems(): List<SentAlert>? = when (val state = _uiState.value) {
        is SentUiState.Content -> state.items
        is SentUiState.ContentWithPageError -> state.items
        else -> null
    }

    private companion object {
        /** Cuántas páginas se recorren, como máximo, buscando la alerta de un push. */
        const val MAX_LOOKUP_PAGES = 3
    }
}
