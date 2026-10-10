package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceRef
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FakeInboxRepository
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FakeReadAlertsStore
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackRating
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetEvidenceUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxPage
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportReason
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportSenderUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SetFeedbackUseCase
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Detalle de una alerta del buzón: evidencia, valoración optimista con rollback, reporte al emisor. */
class InboxDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeInboxRepository()
    private val readStore = FakeReadAlertsStore()

    private val general = EvidenceRef("GENERAL", 0)
    private val plate = EvidenceRef("PLATE", 0)

    private val item = AlertItem(
        id = "alerta-1",
        alertType = "CRASHED",
        createdAt = Instant.EPOCH,
        evidence = listOf(general, plate),
    )

    private fun viewModel(items: List<AlertItem> = listOf(item)): InboxViewModel {
        repository.result = GetInboxResult.Success(InboxPage(items = items, nextCursor = null))
        return InboxViewModel(
            getInbox = GetInboxUseCase(repository),
            getEvidence = GetEvidenceUseCase(repository),
            setFeedback = SetFeedbackUseCase(repository),
            reportSender = ReportSenderUseCase(repository),
            readAlertsStore = readStore,
        )
    }

    private val InboxViewModel.openDetail: AlertDetailUiState get() = checkNotNull(detail.value)

    private fun InboxViewModel.listItem(): AlertItem = (uiState.value as InboxUiState.Content).items.single()

    // -- Abrir y cerrar ---------------------------------------------------------------------

    @Test
    fun `abrir una alerta muestra su detalle y baja cada foto de evidencia`() = runTest {
        val viewModel = viewModel()

        viewModel.openAlert("alerta-1")

        assertEquals(item, viewModel.openDetail.item)
        assertEquals(
            listOf(Triple("alerta-1", "GENERAL", 0), Triple("alerta-1", "PLATE", 0)),
            repository.evidenceCalls,
        )
        assertTrue(viewModel.openDetail.evidence.all { it.load is EvidenceLoad.Loaded })
    }

    @Test
    fun `abrir una alerta que no esta en la lista no hace nada`() = runTest {
        val viewModel = viewModel()

        viewModel.openAlert("otra")

        assertNull(viewModel.detail.value)
        assertEquals(emptyList<Any>(), repository.evidenceCalls)
    }

    @Test
    fun `una alerta sin evidencia abre el detalle sin pedir fotos`() = runTest {
        val viewModel = viewModel(listOf(item.copy(evidence = emptyList())))

        viewModel.openAlert("alerta-1")

        assertEquals(emptyList<EvidenceSlot>(), viewModel.openDetail.evidence)
        assertEquals(emptyList<Any>(), repository.evidenceCalls)
    }

    @Test
    fun `cerrar el detalle sobreescribe con ceros las fotos bajadas`() = runTest {
        val viewModel = viewModel()
        viewModel.openAlert("alerta-1")
        assertEquals(2, repository.loadedArrays.size)
        assertTrue(repository.loadedArrays.all { array -> array.any { it != 0.toByte() } })

        viewModel.closeAlert()

        assertNull(viewModel.detail.value)
        assertTrue(repository.loadedArrays.all { array -> array.all { it == 0.toByte() } })
    }

    @Test
    fun `una foto que llega despues de cerrar el detalle no queda en claro`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.evidenceGate = gate
        val viewModel = viewModel()
        viewModel.openAlert("alerta-1")

        viewModel.closeAlert()
        gate.complete(Unit)

        assertEquals(2, repository.loadedArrays.size)
        assertTrue(repository.loadedArrays.all { array -> array.all { it == 0.toByte() } })
        assertNull(viewModel.detail.value)
    }

    @Test
    fun `onCleared sobreescribe con ceros las fotos del detalle abierto`() = runTest {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel() as T
        }
        val viewModel = ViewModelProvider(store, factory)[InboxViewModel::class.java]
        viewModel.openAlert("alerta-1")

        store.clear()

        assertTrue(repository.loadedArrays.all { array -> array.all { it == 0.toByte() } })
    }

    // -- Evidencia: 410 y errores -----------------------------------------------------------

    @Test
    fun `un 410 deja el placeholder de evidencia no disponible`() = runTest {
        repository.evidenceResult = { EvidenceResult.Expired }
        val viewModel = viewModel()

        viewModel.openAlert("alerta-1")

        assertTrue(viewModel.openDetail.evidence.all { it.load == EvidenceLoad.Expired })
        assertEquals("La evidencia ya no está disponible", ViviMessages.EVIDENCE_UNAVAILABLE)
    }

    @Test
    fun `una foto que falla se puede reintentar sin tocar las demas`() = runTest {
        repository.evidenceResult = { ref ->
            if (ref == plate) EvidenceResult.Failed(InboxError.Network) else EvidenceResult.Loaded(ByteArray(2) { 1 })
        }
        val viewModel = viewModel()
        viewModel.openAlert("alerta-1")
        assertEquals(EvidenceLoad.Failed(InboxError.Network), viewModel.openDetail.evidence[1].load)

        repository.evidenceResult = { EvidenceResult.Loaded(ByteArray(2) { 1 }) }
        viewModel.retryEvidence(plate)

        assertTrue(viewModel.openDetail.evidence[1].load is EvidenceLoad.Loaded)
        assertEquals(3, repository.evidenceCalls.size)
    }

    @Test
    fun `reintentar una foto que no fallo no hace nada`() = runTest {
        val viewModel = viewModel()
        viewModel.openAlert("alerta-1")

        viewModel.retryEvidence(general)

        assertEquals(2, repository.evidenceCalls.size)
    }

    // -- Valoracion: optimista con rollback -------------------------------------------------

    @Test
    fun `la valoracion se ve al instante y se guarda`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.feedbackGate = gate
        val viewModel = viewModel()
        viewModel.openAlert("alerta-1")

        viewModel.onRate(FeedbackRating.UP)

        // Antes de que el servidor responda ya se ve, en el detalle y en la lista.
        assertEquals(FeedbackRating.UP, viewModel.openDetail.item.myFeedback)
        assertEquals(FeedbackRating.UP, viewModel.listItem().myFeedback)

        gate.complete(Unit)

        assertEquals(FeedbackRating.UP, viewModel.openDetail.item.myFeedback)
        assertFalse(viewModel.openDetail.feedbackFailed)
        assertEquals(listOf("alerta-1" to FeedbackRating.UP), repository.feedbackCalls)
    }

    @Test
    fun `si el servidor no guarda la valoracion se deshace y se avisa`() = runTest {
        repository.feedbackResult = FeedbackResult.Failed(InboxError.Unavailable)
        val viewModel = viewModel()
        viewModel.openAlert("alerta-1")

        viewModel.onRate(FeedbackRating.DOWN)

        assertNull(viewModel.openDetail.item.myFeedback)
        assertNull(viewModel.listItem().myFeedback)
        assertTrue(viewModel.openDetail.feedbackFailed)
    }

    @Test
    fun `el rollback vuelve a la valoracion anterior, no a vacio`() = runTest {
        val viewModel = viewModel(listOf(item.copy(myFeedback = FeedbackRating.UP)))
        viewModel.openAlert("alerta-1")
        repository.feedbackResult = FeedbackResult.Failed(InboxError.Network)

        viewModel.onRate(FeedbackRating.DOWN)

        assertEquals(FeedbackRating.UP, viewModel.openDetail.item.myFeedback)
        assertEquals(FeedbackRating.UP, viewModel.listItem().myFeedback)
    }

    @Test
    fun `un nuevo intento limpia el aviso de error`() = runTest {
        repository.feedbackResult = FeedbackResult.Failed(InboxError.Network)
        val viewModel = viewModel()
        viewModel.openAlert("alerta-1")
        viewModel.onRate(FeedbackRating.UP)
        assertTrue(viewModel.openDetail.feedbackFailed)

        repository.feedbackResult = FeedbackResult.Saved
        viewModel.onRate(FeedbackRating.UP)

        assertFalse(viewModel.openDetail.feedbackFailed)
        assertEquals(FeedbackRating.UP, viewModel.openDetail.item.myFeedback)
    }

    @Test
    fun `repetir la misma valoracion no llama al servidor`() = runTest {
        val viewModel = viewModel(listOf(item.copy(myFeedback = FeedbackRating.UP)))
        viewModel.openAlert("alerta-1")

        viewModel.onRate(FeedbackRating.UP)

        assertEquals(emptyList<Any>(), repository.feedbackCalls)
    }

    @Test
    fun `una segunda valoracion mientras la primera esta en vuelo se ignora`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.feedbackGate = gate
        val viewModel = viewModel()
        viewModel.openAlert("alerta-1")

        viewModel.onRate(FeedbackRating.UP)
        viewModel.onRate(FeedbackRating.DOWN)
        gate.complete(Unit)

        assertEquals(listOf("alerta-1" to FeedbackRating.UP), repository.feedbackCalls)
        assertEquals(FeedbackRating.UP, viewModel.openDetail.item.myFeedback)
    }

    // -- Reporte al emisor ------------------------------------------------------------------

    @Test
    fun `un 202 deja el boton en Reportado`() = runTest {
        repository.reportResult = ReportResult.Reported
        val viewModel = viewModel()
        viewModel.openAlert("alerta-1")

        viewModel.onReportSender(ReportReason.FALSE_ALERT)

        assertTrue(viewModel.openDetail.item.reported)
        assertTrue(viewModel.listItem().reported)
        assertEquals(ReportUiState.Idle, viewModel.openDetail.report)
        assertEquals(listOf("alerta-1" to ReportReason.FALSE_ALERT), repository.reportCalls)
        assertEquals("Reportado", ViviMessages.REPORTED)
    }

    @Test
    fun `un 409 tambien deja el boton en Reportado`() = runTest {
        repository.reportResult = ReportResult.AlreadyReported
        val viewModel = viewModel()
        viewModel.openAlert("alerta-1")

        viewModel.onReportSender(ReportReason.SPAM)

        assertTrue(viewModel.openDetail.item.reported)
        assertTrue(viewModel.listItem().reported)
    }

    @Test
    fun `un error del reporte se muestra y no marca como reportado`() = runTest {
        repository.reportResult = ReportResult.Failed(InboxError.RateLimited)
        val viewModel = viewModel()
        viewModel.openAlert("alerta-1")

        viewModel.onReportSender(ReportReason.SPAM)

        assertFalse(viewModel.openDetail.item.reported)
        assertEquals(ReportUiState.Failed(InboxError.RateLimited), viewModel.openDetail.report)
    }

    @Test
    fun `tras un error del reporte se puede reintentar`() = runTest {
        repository.reportResult = ReportResult.Failed(InboxError.Network)
        val viewModel = viewModel()
        viewModel.openAlert("alerta-1")
        viewModel.onReportSender(ReportReason.SPAM)

        repository.reportResult = ReportResult.Reported
        viewModel.onReportSender(ReportReason.SPAM)

        assertTrue(viewModel.openDetail.item.reported)
        assertEquals(2, repository.reportCalls.size)
    }

    @Test
    fun `una alerta ya reportada no se vuelve a reportar`() = runTest {
        val viewModel = viewModel(listOf(item.copy(reported = true)))
        viewModel.openAlert("alerta-1")

        viewModel.onReportSender(ReportReason.SPAM)

        assertEquals(emptyList<Any>(), repository.reportCalls)
    }

    @Test
    fun `un segundo reporte mientras el primero esta en vuelo se ignora`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.reportGate = gate
        val viewModel = viewModel()
        viewModel.openAlert("alerta-1")

        viewModel.onReportSender(ReportReason.SPAM)
        assertEquals(ReportUiState.Sending, viewModel.openDetail.report)
        viewModel.onReportSender(ReportReason.FALSE_ALERT)
        gate.complete(Unit)

        assertEquals(listOf("alerta-1" to ReportReason.SPAM), repository.reportCalls)
    }

    // -- Lista -------------------------------------------------------------------------------

    @Test
    fun `lo que cambia en el detalle no se pierde si llega una pagina nueva`() = runTest {
        repository.result = GetInboxResult.Success(InboxPage(items = listOf(item), nextCursor = "cursor-2"))
        val viewModel = InboxViewModel(
            getInbox = GetInboxUseCase(repository),
            getEvidence = GetEvidenceUseCase(repository),
            setFeedback = SetFeedbackUseCase(repository),
            reportSender = ReportSenderUseCase(repository),
            readAlertsStore = readStore,
        )
        viewModel.openAlert("alerta-1")

        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val page2 = item.copy(id = "alerta-2")
        repository.result = GetInboxResult.Success(InboxPage(items = listOf(page2), nextCursor = null))
        viewModel.loadNextPage()
        viewModel.onRate(FeedbackRating.UP)
        gate.complete(Unit)

        val items = (viewModel.uiState.value as InboxUiState.Content).items
        assertEquals(listOf("alerta-1", "alerta-2"), items.map { it.id })
        assertEquals(FeedbackRating.UP, items.first().myFeedback)
    }
}
