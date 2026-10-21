package ar.felaufernandezbottaro.vinculovial.presentation.scan

import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanErrorPresentationTest {

    private val allErrors = listOf(
        SendAlertError.InvalidEvidence,
        SendAlertError.AlertTypeDisabled,
        SendAlertError.InvalidAlertType,
        SendAlertError.InvalidData,
        SendAlertError.EvidenceTooLarge,
        SendAlertError.UnderReview,
        SendAlertError.AttemptNotFound,
        SendAlertError.PossiblySent,
        SendAlertError.AttemptExpired,
        SendAlertError.RateLimited,
        SendAlertError.Unavailable,
        SendAlertError.Network,
        SendAlertError.Unexpected,
    )

    @Test
    fun `todo error del envio tiene un mensaje propio`() {
        val messages = allErrors.map { it.message() }

        assertTrue(messages.all { it.isNotBlank() })
        assertEquals(messages.size, messages.toSet().size)
    }

    @Test
    fun `429, 502, 503, 504 y red se reintentan a mano, con el mismo mensaje para los 5xx`() {
        listOf(SendAlertError.RateLimited, SendAlertError.Unavailable, SendAlertError.Network).forEach {
            assertEquals(SendAlertRecovery.RETRY, it.recovery())
        }
    }

    @Test
    fun `un intento perdido reinicia el flujo`() {
        assertEquals(SendAlertRecovery.RESTART, SendAlertError.AttemptNotFound.recovery())
    }

    @Test
    fun `si la alerta pudo haberse enviado no se reinicia ni se reintenta, solo se cierra`() {
        assertEquals(SendAlertRecovery.CLOSE, SendAlertError.PossiblySent.recovery())
    }

    @Test
    fun `el aviso de envio incierto es el texto de ViVIAna, sin ofrecer el buzon`() {
        val message = SendAlertError.PossiblySent.message()

        assertEquals("No pudimos confirmar el envío. Puede que tu alerta se haya enviado.", message)
        assertTrue(!message.contains("buzón"))
    }

    @Test
    fun `los errores de evidencia, tipo y cuenta en revision solo se cierran`() {
        listOf(
            SendAlertError.InvalidEvidence,
            SendAlertError.AlertTypeDisabled,
            SendAlertError.InvalidAlertType,
            SendAlertError.InvalidData,
            SendAlertError.EvidenceTooLarge,
            SendAlertError.UnderReview,
        ).forEach { assertEquals(SendAlertRecovery.CLOSE, it.recovery()) }
    }

    @Test
    fun `el mensaje de cuenta en revision es el mismo en el intento y en el envio`() {
        assertEquals(
            ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptResult.UnderReview.message(),
            SendAlertError.UnderReview.message(),
        )
    }

    /** Un error de Vehicle (503 o 400) llega como error de envío genérico: nunca nombra la patente. */
    @Test
    fun `ningun mensaje de error del envio ni del intento menciona la patente ni la red de vehiculos`() {
        val texts = allErrors.map { it.message() } + listOf(
            StartAlertAttemptResult.UnderReview,
            StartAlertAttemptResult.RateLimited,
            StartAlertAttemptResult.Unavailable,
            StartAlertAttemptResult.Unexpected,
        ).map { it.message() }

        listOf("patente", "no está en la red", "no registrada", "vehículo no").forEach { forbidden ->
            assertTrue("'$forbidden' no debería aparecer", texts.none { forbidden in it.lowercase() })
        }
    }
}
