package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FakeInboxRepository
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetEvidenceUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxPage
import ar.felaufernandezbottaro.vinculovial.domain.inbox.MarkAlertSeenUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportSenderUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SetFeedbackUseCase
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** El aviso de alerta nueva abre el chat del dueño de esa alerta, una vez cargada la lista. */
class InboxPushTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeInboxRepository()

    private fun item(id: String) = AlertItem(id = id, alertType = "CRASHED", createdAt = Instant.EPOCH)

    private fun viewModel(vararg ids: String): InboxViewModel {
        repository.result = GetInboxResult.Success(InboxPage(items = ids.map(::item), nextCursor = null))
        return InboxViewModel(
            getInbox = GetInboxUseCase(repository),
            getEvidence = GetEvidenceUseCase(repository),
            setFeedback = SetFeedbackUseCase(repository),
            reportSender = ReportSenderUseCase(repository),
            markAlertSeen = MarkAlertSeenUseCase(repository),
        )
    }

    private fun TestScope.collectEvents(viewModel: InboxViewModel): List<InboxEvent> {
        val received = mutableListOf<InboxEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { received += it } }
        return received
    }

    @Test
    fun `con la alerta ya cargada abre su chat y avisa a la navegacion`() = runTest {
        val viewModel = viewModel("a", "b")
        val events = collectEvents(viewModel)

        viewModel.openAlertFromPush("b")

        assertEquals("b", viewModel.detail.value?.item?.id)
        assertEquals(listOf<InboxEvent>(InboxEvent.OpenDetail), events)
        assertEquals(1, repository.receivedCursors.size)
    }

    @Test
    fun `si la primera pagina todavia esta en vuelo espera a que llegue y no la pide dos veces`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val viewModel = viewModel("a", "b")
        val events = collectEvents(viewModel)

        viewModel.openAlertFromPush("b")
        assertNull(viewModel.detail.value)

        gate.complete(Unit)

        assertEquals("b", viewModel.detail.value?.item?.id)
        assertEquals(listOf<InboxEvent>(InboxEvent.OpenDetail), events)
        assertEquals(1, repository.receivedCursors.size)
    }

    @Test
    fun `si la alerta no estaba en la lista cargada la vuelve a pedir una vez`() = runTest {
        val viewModel = viewModel("a")
        val events = collectEvents(viewModel)

        repository.result = GetInboxResult.Success(InboxPage(items = listOf(item("nueva"), item("a")), nextCursor = null))
        viewModel.openAlertFromPush("nueva")

        assertEquals("nueva", viewModel.detail.value?.item?.id)
        assertEquals(listOf<InboxEvent>(InboxEvent.OpenDetail), events)
        assertEquals(2, repository.receivedCursors.size)
    }

    @Test
    fun `si la alerta no aparece ni tras volver a pedir la lista no abre nada`() = runTest {
        val viewModel = viewModel("a")
        val events = collectEvents(viewModel)

        viewModel.openAlertFromPush("inexistente")

        assertNull(viewModel.detail.value)
        assertEquals(emptyList<InboxEvent>(), events)
        assertEquals(2, repository.receivedCursors.size)
    }

    @Test
    fun `si falla la carga no abre nada y deja el error visible`() = runTest {
        val viewModel = viewModel("a")
        val events = collectEvents(viewModel)

        repository.result = GetInboxResult.Failed(InboxError.Network)
        viewModel.openAlertFromPush("b")

        assertNull(viewModel.detail.value)
        assertEquals(emptyList<InboxEvent>(), events)
        assertEquals(InboxUiState.Error(InboxError.Network), viewModel.uiState.value)
    }

    @Test
    fun `abrir desde el push tambien avisa al servidor que se vio`() = runTest {
        val viewModel = viewModel("a")

        viewModel.openAlertFromPush("a")

        assertEquals(listOf("a"), repository.seenCalls)
    }
}
