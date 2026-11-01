package ar.felaufernandezbottaro.vinculovial.domain.scan

import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceBuffers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SendAlertUseCaseTest {

    private val now = Instant.parse("2030-01-01T00:05:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val alertRepository = FakeAlertRepository()
    private val attemptRepository = FakeAlertAttemptRepository(
        // Lo único que pide este caso de uso al repositorio de intentos es renovar.
        result = FakeAlertAttemptRepository.started(
            attemptId = "attempt-renovado",
            expiresAt = Instant.parse("2030-01-01T00:20:00Z"),
        ),
    )
    private val evidence = EvidenceBuffers()

    private val sendAlert = SendAlertUseCase(alertRepository, attemptRepository, clock)

    private val patEnc = "pat-enc-guardado"

    private fun attempt(
        id: String = "attempt-1",
        expiresAt: Instant = now.plusSeconds(300),
    ) = StartAlertAttemptResult.Started(attemptId = id, expiresAt = expiresAt)

    private val expired = SendAlertResult.Failed(SendAlertError.AttemptExpired)

    // -- Camino feliz -----------------------------------------------------------------------

    @Test
    fun `con el intento vigente envia una sola vez con su attempt_id, el pat_enc, el tipo y las fotos`() = runTest {
        val outcome = sendAlert(attempt(), patEnc, AlertType.CRASHED, evidence)

        assertEquals(SendAlertResult.Sent, outcome.result)
        assertEquals(listOf("attempt-1"), alertRepository.received.map { it.attemptId })
        assertEquals(listOf(patEnc), alertRepository.received.map { it.patEnc })
        assertEquals(listOf(AlertType.CRASHED), alertRepository.received.map { it.alertType })
        assertSame(evidence, alertRepository.received.single().evidence)
        assertEquals(0, attemptRepository.calls)
        assertEquals("attempt-1", outcome.attempt.attemptId)
    }

    @Test
    fun `un error que no es de vencimiento se devuelve tal cual, sin renovar`() = runTest {
        listOf(
            SendAlertError.RateLimited,
            SendAlertError.Unavailable,
            SendAlertError.Network,
            SendAlertError.UnderReview,
            SendAlertError.AttemptNotFound,
            SendAlertError.InvalidEvidence,
            SendAlertError.InvalidData,
            SendAlertError.Unexpected,
        ).forEach { error ->
            alertRepository.received.clear()
            alertRepository.result = SendAlertResult.Failed(error)

            val outcome = sendAlert(attempt(), patEnc, AlertType.CRASHED, evidence)

            assertEquals(SendAlertResult.Failed(error), outcome.result)
            assertEquals(1, alertRepository.received.size)
        }
        assertEquals(0, attemptRepository.calls)
    }

    // -- 410 ATTEMPT_EXPIRED: nuevo intento y un solo reenvio -------------------------------

    @Test
    fun `410 renueva el intento y reenvia con el attempt_id nuevo y el mismo pat_enc`() = runTest {
        alertRepository.queue += expired

        val outcome = sendAlert(attempt(), patEnc, AlertType.CRASHED, evidence)

        assertEquals(SendAlertResult.Sent, outcome.result)
        assertEquals(1, attemptRepository.calls)
        assertEquals(listOf("attempt-1", "attempt-renovado"), alertRepository.received.map { it.attemptId })
        assertEquals(listOf(patEnc, patEnc), alertRepository.received.map { it.patEnc })
        assertEquals("attempt-renovado", outcome.attempt.attemptId)
    }

    @Test
    fun `el reenvio usa las mismas fotos y el mismo tipo`() = runTest {
        alertRepository.queue += expired

        sendAlert(attempt(), patEnc, AlertType.PARKED_ON_GARAGE, evidence)

        assertEquals(listOf(AlertType.PARKED_ON_GARAGE, AlertType.PARKED_ON_GARAGE), alertRepository.received.map { it.alertType })
        assertSame(alertRepository.received[0].evidence, alertRepository.received[1].evidence)
    }

    @Test
    fun `si el 410 se repite tras renovar no hay un tercer envio ni otra renovacion`() = runTest {
        alertRepository.queue += expired
        alertRepository.queue += expired

        val outcome = sendAlert(attempt(), patEnc, AlertType.CRASHED, evidence)

        assertEquals(expired, outcome.result)
        assertEquals(2, alertRepository.received.size)
        assertEquals(1, attemptRepository.calls)
        assertEquals("attempt-renovado", outcome.attempt.attemptId)
    }

    @Test
    fun `un error tras renovar se devuelve tal cual y el intento vigente es el nuevo`() = runTest {
        alertRepository.queue += expired
        alertRepository.queue += SendAlertResult.Failed(SendAlertError.Unavailable)

        val outcome = sendAlert(attempt(), patEnc, AlertType.CRASHED, evidence)

        assertEquals(SendAlertResult.Failed(SendAlertError.Unavailable), outcome.result)
        assertEquals("attempt-renovado", outcome.attempt.attemptId)
    }

    // -- expires_at ya vencido antes de enviar ----------------------------------------------

    @Test
    fun `si el intento ya vencio antes de enviar lo renueva primero y envia una sola vez`() = runTest {
        val outcome = sendAlert(attempt(expiresAt = now.minusSeconds(1)), patEnc, AlertType.CRASHED, evidence)

        assertEquals(SendAlertResult.Sent, outcome.result)
        assertEquals(1, attemptRepository.calls)
        assertEquals(listOf("attempt-renovado"), alertRepository.received.map { it.attemptId })
    }

    @Test
    fun `vencer justo ahora cuenta como vencido`() = runTest {
        sendAlert(attempt(expiresAt = now), patEnc, AlertType.CRASHED, evidence)

        assertEquals(1, attemptRepository.calls)
    }

    @Test
    fun `tras renovar antes de enviar, un 410 no dispara otra renovacion`() = runTest {
        alertRepository.queue += expired

        val outcome = sendAlert(attempt(expiresAt = now.minusSeconds(1)), patEnc, AlertType.CRASHED, evidence)

        assertEquals(expired, outcome.result)
        assertEquals(1, attemptRepository.calls)
        assertEquals(1, alertRepository.received.size)
    }

    // -- Fallas al renovar ------------------------------------------------------------------

    @Test
    fun `si renovar falla no se envia y el error se traduce`() = runTest {
        val cases = mapOf(
            StartAlertAttemptResult.UnderReview to SendAlertError.UnderReview,
            StartAlertAttemptResult.RateLimited to SendAlertError.RateLimited,
            StartAlertAttemptResult.Unavailable to SendAlertError.Unavailable,
            StartAlertAttemptResult.Unexpected to SendAlertError.Unexpected,
        )

        cases.forEach { (renewal, expected) ->
            alertRepository.received.clear()
            attemptRepository.result = renewal

            val outcome = sendAlert(attempt(expiresAt = now.minusSeconds(1)), patEnc, AlertType.CRASHED, evidence)

            assertEquals("renovación $renewal", SendAlertResult.Failed(expected), outcome.result)
            assertEquals(0, alertRepository.received.size)
            assertEquals("attempt-1", outcome.attempt.attemptId)
        }
    }

    @Test
    fun `si el 410 llega y renovar falla no hay reenvio`() = runTest {
        alertRepository.queue += expired
        attemptRepository.result = StartAlertAttemptResult.Unavailable

        val outcome = sendAlert(attempt(), patEnc, AlertType.CRASHED, evidence)

        assertEquals(SendAlertResult.Failed(SendAlertError.Unavailable), outcome.result)
        assertEquals(1, alertRepository.received.size)
    }
}
