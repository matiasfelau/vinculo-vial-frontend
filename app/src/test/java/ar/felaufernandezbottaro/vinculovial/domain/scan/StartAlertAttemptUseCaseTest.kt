package ar.felaufernandezbottaro.vinculovial.domain.scan

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StartAlertAttemptUseCaseTest {

    @Test
    fun `abre un intento en el repositorio, sin patente`() = runTest {
        val repository = FakeAlertAttemptRepository()
        val startAttempt = StartAlertAttemptUseCase(repository)

        startAttempt()

        assertEquals(1, repository.calls)
    }

    @Test
    fun `devuelve el resultado del repositorio sin transformarlo`() = runTest {
        listOf(
            FakeAlertAttemptRepository.started(),
            StartAlertAttemptResult.UnderReview,
            StartAlertAttemptResult.RateLimited,
            StartAlertAttemptResult.Unavailable,
            StartAlertAttemptResult.Unexpected,
        ).forEach { programmed ->
            val startAttempt = StartAlertAttemptUseCase(FakeAlertAttemptRepository(result = programmed))

            assertEquals(programmed, startAttempt())
        }
    }
}
