package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Carga la primera página del buzón al crearse y pagina hacia adelante por cursor. */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val getInbox: GetInboxUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<InboxUiState>(InboxUiState.Loading)
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    /** Barrera contra pedir la misma página dos veces: sin ella, un scroll rápido dispara
     * varias peticiones con el mismo cursor, duplica ítems y quema el cupo de 20/min. */
    private val pageRequestInFlight = AtomicBoolean(false)

    /** El cursor de la próxima página a pedir. `null` una vez agotado el historial. */
    private var nextCursor: String? = null

    init {
        loadFirstPage()
    }

    fun loadFirstPage() {
        viewModelScope.launch {
            _uiState.update { InboxUiState.Loading }

            when (val result = getInbox()) {
                is GetInboxResult.Success -> onFirstPageLoaded(result.page)
                is GetInboxResult.Failed -> _uiState.update { InboxUiState.Error(result.error) }
            }
        }
    }

    /** Un `200` con `items` vacío es `Empty`, nunca `Error`: no tener incidentes es lo deseable. */
    private fun onFirstPageLoaded(page: InboxPage) {
        nextCursor = page.nextCursor
        _uiState.update {
            if (page.items.isEmpty()) {
                InboxUiState.Empty
            } else {
                InboxUiState.Content(items = page.items, nextCursor = page.nextCursor)
            }
        }
    }

    /**
     * Disparado por el scroll (`LazyColumn` cerca del final). Guarda de reentrada obligatoria
     * vía [pageRequestInFlight]: mientras una petición de página está en vuelo, una llamada
     * concurrente se ignora en vez de disparar otra con el mismo cursor.
     */
    fun loadNextPage() {
        val cursor = nextCursor ?: return
        val currentItems = currentItems() ?: return
        if (!pageRequestInFlight.compareAndSet(false, true)) return

        _uiState.update { state -> if (state is InboxUiState.Content) state.copy(isLoadingMore = true) else state }

        viewModelScope.launch {
            when (val result = getInbox(cursor = cursor)) {
                is GetInboxResult.Success -> {
                    nextCursor = result.page.nextCursor
                    _uiState.update {
                        InboxUiState.Content(items = currentItems + result.page.items, nextCursor = result.page.nextCursor)
                    }
                }
                is GetInboxResult.Failed ->
                    _uiState.update { InboxUiState.ContentWithPageError(items = currentItems, error = result.error) }
            }
            pageRequestInFlight.set(false)
        }
    }

    /** Reintenta la misma página que falló, con el mismo cursor -- no se perdió al fallar. */
    fun retryNextPage() = loadNextPage()

    private fun currentItems(): List<AlertItem>? = when (val state = _uiState.value) {
        is InboxUiState.Content -> state.items
        is InboxUiState.ContentWithPageError -> state.items
        else -> null
    }
}
