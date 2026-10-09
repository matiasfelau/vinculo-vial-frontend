package ar.felaufernandezbottaro.vinculovial.domain.scan

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StartAlertAttemptUseCaseTest {

    private val patente = "GUQ856"

    @Test
    fun `pasa la patente reconocida al repositorio tal cual`() = runTest {
        val repository = FakeAlertAttemptRepository()
        val startAttempt = StartAlertAttemptUseCase(repository)

        startAttempt(patente)

        assertEquals(listOf(patente), repository.received)
    }

    @Test
    fun `devuelve el resultado del repositorio sin transformarlo`() = runTest {
        listOf(
            StartAlertAttemptResult.NotInNetwork,
            StartAlertAttemptResult.UnderReview,
            StartAlertAttemptResult.InvalidPlate,
            StartAlertAttemptResult.RateLimited,
            StartAlertAttemptResult.Unavailable,
            StartAlertAttemptResult.Unexpected,
        ).forEach { programmed ->
            val startAttempt = StartAlertAttemptUseCase(FakeAlertAttemptRepository(result = programmed))

            assertEquals(programmed, startAttempt(patente))
        }
    }
}
