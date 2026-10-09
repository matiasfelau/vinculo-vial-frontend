package ar.felaufernandezbottaro.vinculovial.domain.scan

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SendAlertUseCaseTest {

    private val patEnc = "pat-enc-ya-generado-por-el-intento"

    @Test
    fun `pasa el alertType y el pat_enc al repositorio tal cual`() = runTest {
        val repository = FakeAlertRepository()
        val sendAlert = SendAlertUseCase(repository)

        sendAlert(AlertType.CRASHED, patEnc)

        assertEquals(listOf(AlertType.CRASHED to patEnc), repository.received)
    }

    @Test
    fun `devuelve el resultado del repositorio sin transformarlo`() = runTest {
        val repository = FakeAlertRepository(result = SendAlertResult.Failed(SendAlertError.RateLimited))
        val sendAlert = SendAlertUseCase(repository)

        val result = sendAlert(AlertType.CRASHED, patEnc)

        assertEquals(SendAlertResult.Failed(SendAlertError.RateLimited), result)
    }

    /**
     * El requerimiento central del flujo de dos pasos de US-05: el `pat_enc` que
     * `StartAlertAttemptUseCase` generó al escanear se reusa tal cual en `/alerts`, sin volver a
     * escanear ni a cifrar. `SendAlertUseCase` no tiene `HpkeEncryptor` entre sus
     * dependencias -- estructuralmente no podría generar un cifrado nuevo aunque quisiera --
     * así que esta prueba encadena ambos casos de uso con el mismo repositorio falso de
     * intento y confirma que el `pat_enc` que efectivamente viaja a `/alerts` es exactamente
     * el mismo objeto que devolvió el intento, no uno nuevo.
     */
    @Test
    fun `reusa el pat_enc que devolvio el intento, no genera uno nuevo`() = runTest {
        val attemptRepository = FakeAlertAttemptRepository(
            result = StartAlertAttemptResult.Started(
                attemptId = "attempt-de-prueba",
                expiresAt = Instant.parse("2030-01-01T00:10:00Z"),
                patEnc = patEnc,
            ),
        )
        val alertRepository = FakeAlertRepository()
        val startAttempt = StartAlertAttemptUseCase(attemptRepository)
        val sendAlert = SendAlertUseCase(alertRepository)

        val attempt = startAttempt("GUQ856") as StartAlertAttemptResult.Started
        sendAlert(AlertType.CRASHED, attempt.patEnc)

        assertEquals(patEnc, alertRepository.received.single().second)
    }
}
