package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceRef
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackRating
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetEvidenceUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxPage
import ar.felaufernandezbottaro.vinculovial.domain.inbox.MarkAlertSeenUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportReason
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportSenderUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SeenResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SetFeedbackUseCase
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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Carga la primera página del buzón al crearse y pagina hacia adelante por cursor. También
 * maneja el chat de una alerta (evidencia redactada, valoración, reporte al emisor) sobre la
 * misma lista, para que lo que cambia en el chat se vea igual al volver a ella.
 *
 * Qué alertas ya se abrieron lo dice el servidor (`seen` del inbox): al abrir un chat se avisa
 * con `PUT .../seen`, sin bloquear la pantalla. No se guarda ningún id en el dispositivo.
 */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val getInbox: GetInboxUseCase,
    private val getEvidence: GetEvidenceUseCase,
    private val setFeedback: SetFeedbackUseCase,
    private val reportSender: ReportSenderUseCase,
    private val markAlertSeen: MarkAlertSeenUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<InboxUiState>(InboxUiState.Loading)
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    private val _detail = MutableStateFlow<AlertDetailUiState?>(null)

    /** El detalle abierto, o `null` si no hay ninguno. */
    val detail: StateFlow<AlertDetailUiState?> = _detail.asStateFlow()

    private val _events = Channel<InboxEvent>(Channel.BUFFERED)

    /** Efectos únicos que la navegación tiene que ejecutar (abrir el chat de una alerta desde un push). */
    val events: Flow<InboxEvent> = _events.receiveAsFlow()

    /** Ids con un `PUT .../seen` en vuelo: abrir dos veces seguidas no manda dos pedidos. */
    private val seenInFlight = mutableSetOf<String>()

    private var firstPageJob: Job? = null

    /** Barrera contra pedir la misma página dos veces: sin ella, un scroll rápido dispara
     * varias peticiones con el mismo cursor, duplica ítems y quema el cupo de 20/min. */
    private val pageRequestInFlight = AtomicBoolean(false)

    /** Una valoración por vez: con dos en vuelo, el rollback de una podría pisar a la otra. */
    private val feedbackInFlight = AtomicBoolean(false)

    private val reportInFlight = AtomicBoolean(false)

    /** El cursor de la próxima página a pedir. `null` una vez agotado el historial. */
    private var nextCursor: String? = null

    init {
        loadFirstPage()
    }

    fun loadFirstPage(): Job {
        val job = viewModelScope.launch {
            _uiState.update { InboxUiState.Loading }

            when (val result = getInbox()) {
                is GetInboxResult.Success -> onFirstPageLoaded(result.page)
                is GetInboxResult.Failed -> _uiState.update { InboxUiState.Error(result.error) }
            }
        }
        firstPageJob = job
        return job
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
        if (currentItems() == null) return
        if (!pageRequestInFlight.compareAndSet(false, true)) return

        _uiState.update { state -> if (state is InboxUiState.Content) state.copy(isLoadingMore = true) else state }

        viewModelScope.launch {
            when (val result = getInbox(cursor = cursor)) {
                is GetInboxResult.Success -> {
                    nextCursor = result.page.nextCursor
                    // Los ítems vigentes al terminar, no los de antes de pedir: la valoración o el
                    // reporte del detalle pudieron cambiar mientras la página estaba en vuelo.
                    val loaded = currentItems().orEmpty()
                    _uiState.update {
                        InboxUiState.Content(items = loaded + result.page.items, nextCursor = result.page.nextCursor)
                    }
                }
                is GetInboxResult.Failed ->
                    _uiState.update { InboxUiState.ContentWithPageError(items = currentItems().orEmpty(), error = result.error) }
            }
            pageRequestInFlight.set(false)
        }
    }

    /** Reintenta la misma página que falló, con el mismo cursor -- no se perdió al fallar. */
    fun retryNextPage() = loadNextPage()

    // -- Detalle de una alerta ---------------------------------------------------------------

    /** Abre el detalle de [alertId] y empieza a bajar sus fotos de evidencia redactadas. */
    fun openAlert(alertId: String) {
        val item = findLoaded(alertId) ?: return

        wipeEvidence()
        _detail.value = AlertDetailUiState(
            item = item,
            evidence = item.evidence.map { EvidenceSlot(it, EvidenceLoad.Loading) },
        )
        item.evidence.forEach { loadEvidence(item.id, it) }
        if (!item.seen) markSeen(item.id)
    }

    /**
     * Avisa al servidor que se abrió el chat. No bloquea nada: la alerta pasa a "vista" recién
     * cuando el servidor responde 204. Si falla (sin red, 404, límite), queda como no vista y se
     * reintenta la próxima vez que se abra.
     */
    private fun markSeen(alertId: String) {
        if (!seenInFlight.add(alertId)) return

        viewModelScope.launch {
            try {
                if (markAlertSeen(alertId) is SeenResult.Marked) applyItem(alertId) { it.copy(seen = true) }
            } finally {
                seenInFlight.remove(alertId)
            }
        }
    }

    /**
     * Abre el chat de [alertId] a partir de un push de alerta nueva. Espera la primera página (la
     * que se está cargando) y, si la alerta no está —porque la lista es anterior al aviso—, la
     * vuelve a pedir una vez. Si tampoco aparece, no abre nada: el usuario queda en la lista.
     */
    fun openAlertFromPush(alertId: String) {
        viewModelScope.launch {
            firstPageJob?.join()
            if (findLoaded(alertId) == null) loadFirstPage().join()

            if (findLoaded(alertId) == null) return@launch
            openAlert(alertId)
            _events.send(InboxEvent.OpenDetail)
        }
    }

    private fun findLoaded(alertId: String): AlertItem? = currentItems()?.firstOrNull { it.id == alertId }

    /** Cierra el detalle y sobreescribe con ceros las fotos que había bajado. */
    fun closeAlert() {
        wipeEvidence()
        _detail.value = null
    }

    /** Vuelve a pedir una foto que falló. */
    fun retryEvidence(ref: EvidenceRef) {
        val detail = _detail.value ?: return
        val slot = detail.evidence.firstOrNull { it.ref == ref } ?: return
        if (slot.load !is EvidenceLoad.Failed) return

        applySlot(detail.item.id, ref, EvidenceLoad.Loading)
        loadEvidence(detail.item.id, ref)
    }

    private fun loadEvidence(alertId: String, ref: EvidenceRef) {
        viewModelScope.launch {
            val load = when (val result = getEvidence(alertId, ref)) {
                is EvidenceResult.Loaded -> EvidenceLoad.Loaded(result.bytes)
                EvidenceResult.Expired -> EvidenceLoad.Expired
                is EvidenceResult.Failed -> EvidenceLoad.Failed(result.error)
            }

            // Si el detalle ya se cerró o es otro, la foto llegó tarde: no queda en claro.
            if (!applySlot(alertId, ref, load) && load is EvidenceLoad.Loaded) load.bytes.fill(0)
        }
    }

    /** `true` si el casillero se actualizó; `false` si el detalle ya no es el de [alertId]. */
    private fun applySlot(alertId: String, ref: EvidenceRef, load: EvidenceLoad): Boolean {
        var applied = false
        _detail.update { detail ->
            if (detail == null || detail.item.id != alertId) {
                detail
            } else {
                applied = true
                detail.copy(evidence = detail.evidence.map { if (it.ref == ref) it.copy(load = load) else it })
            }
        }
        return applied
    }

    private fun wipeEvidence() {
        _detail.value?.evidence?.forEach { (it.load as? EvidenceLoad.Loaded)?.bytes?.fill(0) }
    }

    /**
     * 👍/👎 con `PUT .../feedback`, optimista: la valoración se ve al instante y, si el
     * servidor no la guarda, se deshace y se avisa. Volver a tocar la misma valoración no hace
     * nada (el contrato no tiene forma de quitarla).
     */
    fun onRate(rating: FeedbackRating) {
        val item = _detail.value?.item ?: return
        if (item.myFeedback == rating) return
        if (!feedbackInFlight.compareAndSet(false, true)) return

        val previous = item.myFeedback
        applyItem(item.id) { it.copy(myFeedback = rating) }
        _detail.update { it?.copy(feedbackFailed = false) }

        viewModelScope.launch {
            try {
                if (setFeedback(item.id, rating) is FeedbackResult.Failed) {
                    applyItem(item.id) { it.copy(myFeedback = previous) }
                    _detail.update { detail -> if (detail?.item?.id == item.id) detail.copy(feedbackFailed = true) else detail }
                }
            } finally {
                feedbackInFlight.set(false)
            }
        }
    }

    /** "Reportar emisor". 202 y 409 dejan el botón en "Reportado"; un error se muestra y se puede reintentar. */
    fun onReportSender(reason: ReportReason) {
        val item = _detail.value?.item ?: return
        if (item.reported || !reportInFlight.compareAndSet(false, true)) return

        _detail.update { it?.copy(report = ReportUiState.Sending) }

        viewModelScope.launch {
            try {
                when (val result = reportSender(item.id, reason)) {
                    ReportResult.Reported, ReportResult.AlreadyReported -> {
                        applyItem(item.id) { it.copy(reported = true) }
                        _detail.update { detail -> if (detail?.item?.id == item.id) detail.copy(report = ReportUiState.Idle) else detail }
                    }
                    is ReportResult.Failed ->
                        _detail.update { detail ->
                            if (detail?.item?.id == item.id) detail.copy(report = ReportUiState.Failed(result.error)) else detail
                        }
                }
            } finally {
                reportInFlight.set(false)
            }
        }
    }

    /** Aplica [transform] al ítem [alertId], en la lista y en el detalle. */
    private fun applyItem(alertId: String, transform: (AlertItem) -> AlertItem) {
        fun List<AlertItem>.updated() = map { if (it.id == alertId) transform(it) else it }

        _uiState.update { state ->
            when (state) {
                is InboxUiState.Content -> state.copy(items = state.items.updated())
                is InboxUiState.ContentWithPageError -> state.copy(items = state.items.updated())
                else -> state
            }
        }
        _detail.update { detail -> if (detail?.item?.id == alertId) detail.copy(item = transform(detail.item)) else detail }
    }

    private fun currentItems(): List<AlertItem>? = when (val state = _uiState.value) {
        is InboxUiState.Content -> state.items
        is InboxUiState.ContentWithPageError -> state.items
        else -> null
    }

    override fun onCleared() {
        wipeEvidence()
        super.onCleared()
    }
}
