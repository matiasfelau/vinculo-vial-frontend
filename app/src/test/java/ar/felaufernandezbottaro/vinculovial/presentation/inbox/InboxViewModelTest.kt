package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FakeInboxRepository
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxPage
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class InboxViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeInboxRepository()

    private fun viewModel() = InboxViewModel(GetInboxUseCase(repository))

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
}
