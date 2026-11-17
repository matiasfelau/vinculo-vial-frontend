package ar.felaufernandezbottaro.vinculovial.domain.scan

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class StartAlertAttemptUseCaseTest {

    @Test
    fun `abre un intento en el repositorio con el pat_enc, nunca la patente`() = runTest {
        val repository = FakeAlertAttemptRepository()
        val startAttempt = StartAlertAttemptUseCase(repository)

        startAttempt("pat-enc-de-prueba")

        assertEquals(1, repository.calls)
        assertEquals(listOf("pat-enc-de-prueba"), repository.receivedPatEnc)
    }

    @Test
    fun `devuelve el resultado del repositorio sin transformarlo`() = runTest {
        listOf(
            FakeAlertAttemptRepository.started(),
            StartAlertAttemptResult.DailyLimit(Instant.parse("2030-01-02T15:30:00Z")),
            StartAlertAttemptResult.UnderReview,
            StartAlertAttemptResult.RateLimited,
            StartAlertAttemptResult.Unavailable,
            StartAlertAttemptResult.Unexpected,
        ).forEach { programmed ->
            val startAttempt = StartAlertAttemptUseCase(FakeAlertAttemptRepository(result = programmed))

            assertEquals(programmed, startAttempt("pat-enc-de-prueba"))
        }
    }
}
