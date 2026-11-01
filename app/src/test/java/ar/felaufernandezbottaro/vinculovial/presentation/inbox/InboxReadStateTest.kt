package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceRef
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FakeInboxRepository
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetEvidenceUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxPage
import ar.felaufernandezbottaro.vinculovial.domain.inbox.MarkAlertSeenUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportSenderUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SeenResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SetFeedbackUseCase
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * "No leído" lo dice el servidor (`seen` del inbox). Al abrir el chat se avisa con
 * `PUT .../seen`, sin bloquear la pantalla; no se guarda ningún id en el dispositivo.
 */
class InboxReadStateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeInboxRepository()

    private fun item(id: String, seen: Boolean = false) = AlertItem(
        id = id,
        alertType = "CRASHED",
        createdAt = Instant.EPOCH,
        evidence = listOf(EvidenceRef("GENERAL", 0)),
        seen = seen,
    )

    private fun viewModel(items: List<AlertItem> = listOf(item("a"), item("b"), item("c", seen = true))): InboxViewModel {
        repository.result = GetInboxResult.Success(InboxPage(items = items, nextCursor = null))
        return InboxViewModel(
            getInbox = GetInboxUseCase(repository),
            getEvidence = GetEvidenceUseCase(repository),
            setFeedback = SetFeedbackUseCase(repository),
            reportSender = ReportSenderUseCase(repository),
            markAlertSeen = MarkAlertSeenUseCase(repository),
        )
    }

    private fun InboxViewModel.items(): List<AlertItem> = (uiState.value as InboxUiState.Content).items

    // -- El no leído sale de `seen` ----------------------------------------------------------

    @Test
    fun `lo no leido es lo que el servidor manda con seen en false`() = runTest {
        val viewModel = viewModel()

        assertEquals(listOf(false, false, true), viewModel.items().map { it.seen })
        assertEquals(2, unreadCount(viewModel.items()))
    }

    @Test
    fun `sin alertas cargadas no hay nada sin leer`() {
        assertEquals(0, unreadCount(emptyList()))
    }

    // -- Abrir el chat avisa al servidor ------------------------------------------------------

    @Test
    fun `abrir una alerta sin ver manda el PUT seen y la marca como vista al confirmarse`() = runTest {
        val viewModel = viewModel()

        viewModel.openAlert("a")

        assertEquals(listOf("a"), repository.seenCalls)
        assertTrue(viewModel.items().first { it.id == "a" }.seen)
        assertEquals(1, unreadCount(viewModel.items()))
    }

    @Test
    fun `abrir una alerta ya vista no vuelve a avisar al servidor`() = runTest {
        val viewModel = viewModel()

        viewModel.openAlert("c")

        assertEquals(emptyList<String>(), repository.seenCalls)
    }

    @Test
    fun `si el PUT seen falla la alerta sigue sin ver y se reintenta la proxima vez`() = runTest {
        repository.seenResult = SeenResult.Failed(InboxError.Network)
        val viewModel = viewModel()

        viewModel.openAlert("a")
        assertFalse(viewModel.items().first { it.id == "a" }.seen)

        viewModel.closeAlert()
        repository.seenResult = SeenResult.Marked
        viewModel.openAlert("a")

        assertEquals(listOf("a", "a"), repository.seenCalls)
        assertTrue(viewModel.items().first { it.id == "a" }.seen)
    }

    @Test
    fun `un 404 al avisar no rompe nada ni abre un error`() = runTest {
        repository.seenResult = SeenResult.Failed(InboxError.NotFound)
        val viewModel = viewModel()

        viewModel.openAlert("a")

        assertTrue(viewModel.uiState.value is InboxUiState.Content)
        assertEquals("a", viewModel.detail.value?.item?.id)
    }

    @Test
    fun `el PUT seen no bloquea la pantalla, el chat se abre con el aviso en vuelo`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.seenGate = gate
        val viewModel = viewModel()

        viewModel.openAlert("a")

        assertEquals("a", viewModel.detail.value?.item?.id)
        assertFalse(viewModel.items().first { it.id == "a" }.seen)

        gate.complete(Unit)
        assertTrue(viewModel.items().first { it.id == "a" }.seen)
    }

    @Test
    fun `abrir dos veces seguidas con el aviso en vuelo manda un solo PUT`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.seenGate = gate
        val viewModel = viewModel()

        viewModel.openAlert("a")
        viewModel.closeAlert()
        viewModel.openAlert("a")

        assertEquals(listOf("a"), repository.seenCalls)
        gate.complete(Unit)
    }

    @Test
    fun `la vista tambien se refleja en el detalle abierto`() = runTest {
        val viewModel = viewModel()

        viewModel.openAlert("a")

        assertTrue(checkNotNull(viewModel.detail.value).item.seen)
    }

    @Test
    fun `una alerta que no esta en la lista no avisa nada`() = runTest {
        val viewModel = viewModel()

        viewModel.openAlert("fantasma")

        assertEquals(emptyList<String>(), repository.seenCalls)
    }

    @Test
    fun `se avisa aunque la evidencia falle`() = runTest {
        repository.evidenceResult = { EvidenceResult.Failed(InboxError.Network) }
        val viewModel = viewModel()

        viewModel.openAlert("a")

        assertEquals(listOf("a"), repository.seenCalls)
    }

    @Test
    fun `no se pide nada mas que la evidencia y el aviso al abrir`() = runTest {
        val viewModel = viewModel()

        viewModel.openAlert("a")

        assertEquals(listOf(Triple("a", "GENERAL", 0)), repository.evidenceCalls)
        assertEquals(emptyList<Any>(), repository.feedbackCalls)
        assertEquals(emptyList<Any>(), repository.reportCalls)
    }

    @Test
    fun `la pestaña por defecto es Recibidos y Enviados es la otra`() {
        assertEquals(InboxTab.RECEIVED, InboxTab.entries.first())
        assertEquals(listOf("Recibidos", "Enviados"), InboxTab.entries.map { it.title })
    }
}
