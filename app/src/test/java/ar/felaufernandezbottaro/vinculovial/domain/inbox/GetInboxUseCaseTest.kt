package ar.felaufernandezbottaro.vinculovial.domain.inbox

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetInboxUseCaseTest {

    private val repository = FakeInboxRepository()
    private val useCase = GetInboxUseCase(repository)

    @Test
    fun `sin cursor delega en el repositorio sin cursor`() = runTest {
        useCase()

        assertEquals(listOf<String?>(null), repository.receivedCursors)
    }

    @Test
    fun `con cursor lo reenvia tal cual al repositorio`() = runTest {
        useCase(cursor = "cursor-opaco")

        assertEquals(listOf("cursor-opaco"), repository.receivedCursors)
    }

    @Test
    fun `devuelve el resultado del repositorio sin transformarlo`() = runTest {
        val page = InboxPage(items = listOf(AlertItem("id-1", "CRASHED", java.time.Instant.EPOCH)), nextCursor = "c2")
        repository.result = GetInboxResult.Success(page)

        val result = useCase()

        assertEquals(GetInboxResult.Success(page), result)
    }

    @Test
    fun `propaga un fallo del repositorio`() = runTest {
        repository.result = GetInboxResult.Failed(InboxError.RateLimited)

        val result = useCase()

        assertEquals(GetInboxResult.Failed(InboxError.RateLimited), result)
    }
}
