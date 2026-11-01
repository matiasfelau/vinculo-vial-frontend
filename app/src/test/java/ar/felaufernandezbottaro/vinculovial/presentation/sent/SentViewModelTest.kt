package ar.felaufernandezbottaro.vinculovial.presentation.sent

import ar.felaufernandezbottaro.vinculovial.domain.inbox.FakeInboxRepository
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetSentResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetSentUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlert
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlertStatus
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentPage
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Lista de Enviados, paginación infinita, estados vacío/carga/error y apertura del chat del emisor. */
class SentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeInboxRepository()

    private fun viewModel() = SentViewModel(getSent = GetSentUseCase(repository))

    private fun alert(id: String, status: SentAlertStatus = SentAlertStatus.PENDING_VALIDATION, seenAt: Instant? = null) =
        SentAlert(id = id, alertType = "CRASHED", createdAt = Instant.EPOCH, status = status, seenAt = seenAt)

    private fun page(vararg ids: String, next: String? = null) =
        GetSentResult.Success(SentPage(items = ids.map { alert(it) }, nextCursor = next))

    private fun SentViewModel.items(): List<SentAlert> = (uiState.value as SentUiState.Content).items

    // -- Primera carga ------------------------------------------------------------------------

    @Test
    fun `no carga hasta que se muestra la pestaña`() = runTest {
        viewModel()

        assertEquals(emptyList<String?>(), repository.sentCursors)
    }

    @Test
    fun `arranca en Loading y termina en Content con los items y el cursor`() = runTest {
        repository.sentResult = page("a", "b", next = "cursor-2")
        val viewModel = viewModel()
        assertEquals(SentUiState.Loading, viewModel.uiState.value)

        viewModel.refresh()

        val state = viewModel.uiState.value as SentUiState.Content
        assertEquals(listOf("a", "b"), state.items.map { it.id })
        assertEquals("cursor-2", state.nextCursor)
    }

    @Test
    fun `la primera carga no manda cursor`() = runTest {
        repository.sentResult = page("a")

        viewModel().refresh()

        assertEquals(listOf<String?>(null), repository.sentCursors)
    }

    /** `items` vacío es `Empty`, nunca `Error`: no haber enviado nada no es una falla. */
    @Test
    fun `una lista vacia termina en Empty, nunca en Error`() = runTest {
        repository.sentResult = page()
        val viewModel = viewModel()

        viewModel.refresh()

        assertEquals(SentUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `un fallo en la primera carga termina en Error con el motivo`() = runTest {
        repository.sentResult = GetSentResult.Failed(InboxError.RateLimited)
        val viewModel = viewModel()

        viewModel.refresh()

        assertEquals(SentUiState.Error(InboxError.RateLimited), viewModel.uiState.value)
    }

    @Test
    fun `reintentar tras un error vuelve a pedir la primera pagina`() = runTest {
        repository.sentResult = GetSentResult.Failed(InboxError.Network)
        val viewModel = viewModel()
        viewModel.refresh()

        repository.sentResult = page("a")
        viewModel.refresh()

        assertEquals(listOf("a"), viewModel.items().map { it.id })
        assertEquals(listOf<String?>(null, null), repository.sentCursors)
    }

    @Test
    fun `refrescar con una carga en vuelo no lanza otro pedido`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.sentGate = gate
        repository.sentResult = page("a")
        val viewModel = viewModel()

        val first = viewModel.refresh()
        val second = viewModel.refresh()

        assertSame(first, second)
        gate.complete(Unit)
        assertEquals(1, repository.sentCursors.size)
    }

    // -- Refresco silencioso ------------------------------------------------------------------

    @Test
    fun `refrescar con lista cargada no vuelve a Loading y reemplaza los items`() = runTest {
        repository.sentResult = page("a")
        val viewModel = viewModel()
        viewModel.refresh()

        val gate = CompletableDeferred<Unit>()
        repository.sentGate = gate
        repository.sentResult = page("b")
        viewModel.refresh()

        assertEquals(listOf("a"), viewModel.items().map { it.id })
        gate.complete(Unit)
        assertEquals(listOf("b"), viewModel.items().map { it.id })
    }

    @Test
    fun `si el refresco silencioso falla se conserva la lista que habia`() = runTest {
        repository.sentResult = page("a")
        val viewModel = viewModel()
        viewModel.refresh()

        repository.sentResult = GetSentResult.Failed(InboxError.Network)
        viewModel.refresh()

        assertEquals(listOf("a"), viewModel.items().map { it.id })
    }

    // -- Paginación infinita ------------------------------------------------------------------

    @Test
    fun `la pagina siguiente se pide con el cursor y se suma a la lista`() = runTest {
        repository.sentResult = page("a", next = "cursor-2")
        val viewModel = viewModel()
        viewModel.refresh()

        repository.sentResult = page("b", next = null)
        viewModel.loadNextPage()

        val state = viewModel.uiState.value as SentUiState.Content
        assertEquals(listOf("a", "b"), state.items.map { it.id })
        assertNull(state.nextCursor)
        assertEquals(listOf<String?>(null, "cursor-2"), repository.sentCursors)
    }

    @Test
    fun `sin cursor no hay pagina siguiente`() = runTest {
        repository.sentResult = page("a", next = null)
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.loadNextPage()

        assertEquals(1, repository.sentCursors.size)
    }

    @Test
    fun `con una pagina en vuelo un segundo pedido se ignora`() = runTest {
        repository.sentResult = page("a", next = "cursor-2")
        val viewModel = viewModel()
        viewModel.refresh()

        val gate = CompletableDeferred<Unit>()
        repository.sentGate = gate
        repository.sentResult = page("b")
        viewModel.loadNextPage()
        viewModel.loadNextPage()
        gate.complete(Unit)

        assertEquals(listOf<String?>(null, "cursor-2"), repository.sentCursors)
        assertEquals(listOf("a", "b"), viewModel.items().map { it.id })
    }

    @Test
    fun `mientras carga la pagina siguiente se marca isLoadingMore sin reemplazar la lista`() = runTest {
        repository.sentResult = page("a", next = "cursor-2")
        val viewModel = viewModel()
        viewModel.refresh()

        val gate = CompletableDeferred<Unit>()
        repository.sentGate = gate
        viewModel.loadNextPage()

        val state = viewModel.uiState.value as SentUiState.Content
        assertTrue(state.isLoadingMore)
        assertEquals(listOf("a"), state.items.map { it.id })
        gate.complete(Unit)
    }

    @Test
    fun `un fallo de pagina conserva la lista y permite reintentar con el mismo cursor`() = runTest {
        repository.sentResult = page("a", next = "cursor-2")
        val viewModel = viewModel()
        viewModel.refresh()

        repository.sentResult = GetSentResult.Failed(InboxError.Unavailable)
        viewModel.loadNextPage()

        val failed = viewModel.uiState.value as SentUiState.ContentWithPageError
        assertEquals(listOf("a"), failed.items.map { it.id })
        assertEquals(InboxError.Unavailable, failed.error)

        repository.sentResult = page("b")
        viewModel.retryNextPage()

        assertEquals(listOf("a", "b"), viewModel.items().map { it.id })
        assertEquals(listOf<String?>(null, "cursor-2", "cursor-2"), repository.sentCursors)
    }

    // -- Chat del emisor ----------------------------------------------------------------------

    @Test
    fun `abrir el chat de una alerta cargada la deja seleccionada y cerrarlo la limpia`() = runTest {
        repository.sentResult = page("a", "b")
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.openChat("b")
        assertEquals("b", viewModel.chat.value?.id)

        viewModel.closeChat()
        assertNull(viewModel.chat.value)
    }

    @Test
    fun `abrir el chat de una alerta que no esta en la lista no abre nada`() = runTest {
        repository.sentResult = page("a")
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.openChat("fantasma")

        assertNull(viewModel.chat.value)
    }

    // -- Push SENT_UPDATED ---------------------------------------------------------------------

    private fun kotlinx.coroutines.test.TestScope.collectEvents(viewModel: SentViewModel): List<SentEvent> {
        val received = mutableListOf<SentEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { received += it } }
        return received
    }

    @Test
    fun `un push con la alerta en la primera pagina refresca, abre el chat y avisa a la navegacion`() = runTest {
        repository.sentResult = page("a", "b")
        val viewModel = viewModel()
        val events = collectEvents(viewModel)

        viewModel.openChatFromPush("b")

        assertEquals("b", viewModel.chat.value?.id)
        assertEquals(listOf<SentEvent>(SentEvent.OpenChat), events)
    }

    @Test
    fun `un push vuelve a pedir la lista aunque ya estuviera cargada, porque el estado cambio`() = runTest {
        repository.sentResult = GetSentResult.Success(SentPage(listOf(alert("a")), null))
        val viewModel = viewModel()
        viewModel.refresh()

        repository.sentResult = GetSentResult.Success(
            SentPage(listOf(alert("a", SentAlertStatus.ACTIVE, seenAt = Instant.EPOCH)), null),
        )
        viewModel.openChatFromPush("a")

        assertEquals(SentAlertStatus.ACTIVE, viewModel.chat.value?.status)
        assertEquals(Instant.EPOCH, viewModel.chat.value?.seenAt)
    }

    @Test
    fun `si la alerta del push esta en la segunda pagina la busca paginando`() = runTest {
        repository.sentResultFor = { cursor ->
            if (cursor == null) page("a", next = "c2") else page("b", next = null)
        }
        val viewModel = viewModel()
        val events = collectEvents(viewModel)

        viewModel.openChatFromPush("b")

        assertEquals("b", viewModel.chat.value?.id)
        assertEquals(listOf<String?>(null, "c2"), repository.sentCursors)
        assertEquals(listOf<SentEvent>(SentEvent.OpenChat), events)
    }

    @Test
    fun `si la alerta del push no aparece corta tras el tope de paginas y no abre nada`() = runTest {
        repository.sentResultFor = { cursor ->
            page("x${cursor.orEmpty()}", next = "${cursor.orEmpty()}n")
        }
        val viewModel = viewModel()
        val events = collectEvents(viewModel)

        viewModel.openChatFromPush("inexistente")

        assertNull(viewModel.chat.value)
        assertEquals(emptyList<SentEvent>(), events)
        assertEquals(3, repository.sentCursors.size)
        assertTrue(viewModel.uiState.value is SentUiState.Content)
    }

    @Test
    fun `si la alerta del push no aparece y se agotan las paginas no abre nada`() = runTest {
        repository.sentResult = page("a", next = null)
        val viewModel = viewModel()
        val events = collectEvents(viewModel)

        viewModel.openChatFromPush("inexistente")

        assertNull(viewModel.chat.value)
        assertEquals(emptyList<SentEvent>(), events)
        assertEquals(1, repository.sentCursors.size)
    }

    @Test
    fun `si falla la pagina buscando la alerta del push no reintenta en bucle`() = runTest {
        repository.sentResultFor = { cursor ->
            if (cursor == null) page("a", next = "c2") else GetSentResult.Failed(InboxError.Unavailable)
        }
        val viewModel = viewModel()
        val events = collectEvents(viewModel)

        viewModel.openChatFromPush("b")

        assertNull(viewModel.chat.value)
        assertEquals(emptyList<SentEvent>(), events)
        assertEquals(listOf<String?>(null, "c2"), repository.sentCursors)
        assertTrue(viewModel.uiState.value is SentUiState.ContentWithPageError)
    }

    @Test
    fun `si falla la primera carga del push no abre nada y deja el error visible`() = runTest {
        repository.sentResult = GetSentResult.Failed(InboxError.Network)
        val viewModel = viewModel()
        val events = collectEvents(viewModel)

        viewModel.openChatFromPush("a")

        assertNull(viewModel.chat.value)
        assertEquals(emptyList<SentEvent>(), events)
        assertEquals(SentUiState.Error(InboxError.Network), viewModel.uiState.value)
    }
}
