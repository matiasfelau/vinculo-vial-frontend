package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FakeInboxRepository
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetEvidenceUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxPage
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportSenderUseCase
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

class InboxViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeInboxRepository()

    private fun viewModel() = InboxViewModel(
        getInbox = GetInboxUseCase(repository),
        getEvidence = GetEvidenceUseCase(repository),
        setFeedback = SetFeedbackUseCase(repository),
        reportSender = ReportSenderUseCase(repository),
    )

    private val item = AlertItem(id = "id-1", alertType = "CRASHED", createdAt = Instant.EPOCH)

    @Test
    fun `arranca en Loading y termina en Content cuando hay items`() = runTest {
        repository.result = GetInboxResult.Success(InboxPage(items = listOf(item), nextCursor = "cursor-2"))

        val viewModel = viewModel()

        val state = viewModel.uiState.value
        assertTrue(state is InboxUiState.Content)
        assertEquals(listOf(item), (state as InboxUiState.Content).items)
        assertEquals("cursor-2", state.nextCursor)
    }

    /** El caso obligatorio de CLAUDE.md: `items` vacío es `Empty`, nunca `Error`. */
    @Test
    fun `una bandeja vacia termina en Empty, nunca en Error`() = runTest {
        repository.result = GetInboxResult.Success(InboxPage(items = emptyList(), nextCursor = null))

        val viewModel = viewModel()

        assertEquals(InboxUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `un fallo en la primera carga termina en Error con el motivo`() = runTest {
        repository.result = GetInboxResult.Failed(InboxError.RateLimited)

        val viewModel = viewModel()

        assertEquals(InboxUiState.Error(InboxError.RateLimited), viewModel.uiState.value)
    }

    @Test
    fun `la primera carga no manda cursor`() = runTest {
        viewModel()

        assertEquals(listOf<String?>(null), repository.receivedCursors)
    }

    @Test
    fun `reintentar tras un error vuelve a pedir la primera pagina`() = runTest {
        repository.result = GetInboxResult.Failed(InboxError.Network)
        val viewModel = viewModel()
        assertEquals(InboxUiState.Error(InboxError.Network), viewModel.uiState.value)

        repository.result = GetInboxResult.Success(InboxPage(items = listOf(item), nextCursor = null))
        viewModel.loadFirstPage()

        assertTrue(viewModel.uiState.value is InboxUiState.Content)
    }

    @Test
    fun `el orden descendente que devuelve el repositorio se preserva tal cual`() = runTest {
        val newest = item.copy(id = "id-newest", createdAt = Instant.parse("2026-08-18T12:00:00Z"))
        val oldest = item.copy(id = "id-oldest", createdAt = Instant.parse("2026-08-17T12:00:00Z"))
        repository.result = GetInboxResult.Success(InboxPage(items = listOf(newest, oldest), nextCursor = null))

        val viewModel = viewModel()

        val state = viewModel.uiState.value as InboxUiState.Content
        assertEquals(listOf(newest, oldest), state.items)
    }

    // -- Paginación por scroll -------------------------------------------------------------

    private val page2Item = item.copy(id = "id-2")

    @Test
    fun `cargar la pagina siguiente acumula items sin duplicar los ya cargados`() = runTest {
        repository.result = GetInboxResult.Success(InboxPage(items = listOf(item), nextCursor = "cursor-2"))
        val viewModel = viewModel()

        repository.result = GetInboxResult.Success(InboxPage(items = listOf(page2Item), nextCursor = null))
        viewModel.loadNextPage()

        val state = viewModel.uiState.value as InboxUiState.Content
        assertEquals(listOf(item, page2Item), state.items)
    }

    @Test
    fun `la pagina siguiente reenvia el cursor recibido, sin interpretarlo`() = runTest {
        repository.result = GetInboxResult.Success(InboxPage(items = listOf(item), nextCursor = "cursor-opaco-2"))
        val viewModel = viewModel()

        viewModel.loadNextPage()

        assertEquals(listOf<String?>(null, "cursor-opaco-2"), repository.receivedCursors)
    }

    @Test
    fun `next_cursor null detiene la paginacion, no se piden mas paginas`() = runTest {
        repository.result = GetInboxResult.Success(InboxPage(items = listOf(item), nextCursor = null))
        val viewModel = viewModel()

        viewModel.loadNextPage()

        assertEquals(listOf<String?>(null), repository.receivedCursors)
        assertEquals(listOf(item), (viewModel.uiState.value as InboxUiState.Content).items)
    }

    @Test
    fun `un error en la pagina siguiente conserva los items ya cargados`() = runTest {
        repository.result = GetInboxResult.Success(InboxPage(items = listOf(item), nextCursor = "cursor-2"))
        val viewModel = viewModel()

        repository.result = GetInboxResult.Failed(InboxError.Network)
        viewModel.loadNextPage()

        val state = viewModel.uiState.value
        assertTrue(state is InboxUiState.ContentWithPageError)
        assertEquals(listOf(item), (state as InboxUiState.ContentWithPageError).items)
        assertEquals(InboxError.Network, state.error)
    }

    @Test
    fun `reintentar tras un error de pagina usa el mismo cursor y recupera la lista`() = runTest {
        repository.result = GetInboxResult.Success(InboxPage(items = listOf(item), nextCursor = "cursor-2"))
        val viewModel = viewModel()
        repository.result = GetInboxResult.Failed(InboxError.Unexpected)
        viewModel.loadNextPage()

        repository.result = GetInboxResult.Success(InboxPage(items = listOf(page2Item), nextCursor = null))
        viewModel.retryNextPage()

        assertEquals(listOf<String?>(null, "cursor-2", "cursor-2"), repository.receivedCursors)
        val state = viewModel.uiState.value as InboxUiState.Content
        assertEquals(listOf(item, page2Item), state.items)
    }

    @Test
    fun `sin nextCursor pedir la pagina siguiente no llama al repositorio`() = runTest {
        repository.result = GetInboxResult.Success(InboxPage(items = listOf(item), nextCursor = null))
        val viewModel = viewModel()

        viewModel.loadNextPage()

        assertEquals(1, repository.receivedCursors.size)
    }

    /**
     * El caso obligatorio de CLAUDE.md: un scroll rápido no debe disparar peticiones
     * concurrentes con el mismo cursor. Se usa un `gate` para mantener la primera petición en
     * vuelo mientras se dispara una segunda, y se verifica que esta última no llegó al
     * repositorio.
     */
    @Test
    fun `una segunda llamada mientras la primera esta en vuelo no duplica la peticion`() = runTest {
        repository.result = GetInboxResult.Success(InboxPage(items = listOf(item), nextCursor = "cursor-2"))
        val viewModel = viewModel()

        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        repository.result = GetInboxResult.Success(InboxPage(items = listOf(page2Item), nextCursor = null))

        viewModel.loadNextPage()
        viewModel.loadNextPage()

        assertEquals(listOf<String?>(null, "cursor-2"), repository.receivedCursors)
        assertTrue((viewModel.uiState.value as InboxUiState.Content).isLoadingMore)

        gate.complete(Unit)

        val state = viewModel.uiState.value as InboxUiState.Content
        assertFalse(state.isLoadingMore)
        assertEquals(listOf(item, page2Item), state.items)
    }
}
