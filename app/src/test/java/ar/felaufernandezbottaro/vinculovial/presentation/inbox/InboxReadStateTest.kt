package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceRef
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FakeInboxRepository
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FakeReadAlertsStore
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetEvidenceUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxPage
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportSenderUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SetFeedbackUseCase
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** "No leído" es local: sólo se sabe qué `alert_id` se abrió en este dispositivo, y no se envía a ningún lado. */
class InboxReadStateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeInboxRepository()

    private fun item(id: String) = AlertItem(
        id = id,
        alertType = "CRASHED",
        createdAt = Instant.EPOCH,
        evidence = listOf(EvidenceRef("GENERAL", 0)),
    )

    private val items = listOf(item("a"), item("b"), item("c"))

    private fun viewModel(store: FakeReadAlertsStore = FakeReadAlertsStore()): InboxViewModel {
        repository.result = GetInboxResult.Success(InboxPage(items = items, nextCursor = null))
        return InboxViewModel(
            getInbox = GetInboxUseCase(repository),
            getEvidence = GetEvidenceUseCase(repository),
            setFeedback = SetFeedbackUseCase(repository),
            reportSender = ReportSenderUseCase(repository),
            readAlertsStore = store,
        )
    }

    @Test
    fun `al arrancar nada se abrio, todas las alertas estan sin leer`() = runTest {
        val viewModel = viewModel()

        assertEquals(emptySet<String>(), viewModel.readAlertIds.value)
        assertEquals(3, unreadCount(items, viewModel.readAlertIds.value))
    }

    @Test
    fun `lo abierto en una sesion anterior arranca como leido`() = runTest {
        val viewModel = viewModel(FakeReadAlertsStore(initial = setOf("b")))

        assertEquals(setOf("b"), viewModel.readAlertIds.value)
        assertEquals(2, unreadCount(items, viewModel.readAlertIds.value))
    }

    @Test
    fun `abrir una alerta la marca como leida y la guarda en el dispositivo`() = runTest {
        val store = FakeReadAlertsStore()
        val viewModel = viewModel(store)

        viewModel.openAlert("a")

        assertEquals(setOf("a"), viewModel.readAlertIds.value)
        assertEquals(setOf("a"), store.readIds())
        assertEquals(2, unreadCount(items, viewModel.readAlertIds.value))
    }

    @Test
    fun `abrir otra alerta suma, no reemplaza`() = runTest {
        val viewModel = viewModel()

        viewModel.openAlert("a")
        viewModel.closeAlert()
        viewModel.openAlert("c")

        assertEquals(setOf("a", "c"), viewModel.readAlertIds.value)
        assertEquals(1, unreadCount(items, viewModel.readAlertIds.value))
    }

    @Test
    fun `volver a abrir una alerta no la duplica`() = runTest {
        val viewModel = viewModel()

        viewModel.openAlert("a")
        viewModel.closeAlert()
        viewModel.openAlert("a")

        assertEquals(setOf("a"), viewModel.readAlertIds.value)
    }

    @Test
    fun `una alerta que no esta en la lista no se marca`() = runTest {
        val store = FakeReadAlertsStore()
        val viewModel = viewModel(store)

        viewModel.openAlert("fantasma")

        assertEquals(emptySet<String>(), viewModel.readAlertIds.value)
        assertEquals(emptyList<String>(), store.marked)
    }

    @Test
    fun `se marca como leida aunque la evidencia falle`() = runTest {
        repository.evidenceResult = { EvidenceResult.Failed(InboxError.Network) }
        val viewModel = viewModel()

        viewModel.openAlert("a")

        assertEquals(setOf("a"), viewModel.readAlertIds.value)
    }

    @Test
    fun `no leer una alerta no pide nada al servidor, solo la evidencia al abrirla`() = runTest {
        val viewModel = viewModel()

        viewModel.openAlert("a")

        assertEquals(listOf(Triple("a", "GENERAL", 0)), repository.evidenceCalls)
        assertEquals(emptyList<Any>(), repository.feedbackCalls)
        assertEquals(emptyList<Any>(), repository.reportCalls)
    }

    @Test
    fun `sin alertas cargadas no hay nada sin leer`() {
        assertEquals(0, unreadCount(emptyList(), emptySet()))
    }

    @Test
    fun `la pestaña por defecto es Recibidos y Enviados es la otra`() {
        assertEquals(InboxTab.RECEIVED, InboxTab.entries.first())
        assertEquals(listOf("Recibidos", "Enviados"), InboxTab.entries.map { it.title })
    }
}
