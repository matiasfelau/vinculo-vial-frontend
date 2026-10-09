package ar.felaufernandezbottaro.vinculovial.presentation.scan

import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
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
    fun `429, 503 y red se reintentan a mano`() {
        listOf(SendAlertError.RateLimited, SendAlertError.Unavailable, SendAlertError.Network).forEach {
            assertEquals(SendAlertRecovery.RETRY, it.recovery())
        }
    }

    @Test
    fun `un intento perdido reinicia el flujo`() {
        assertEquals(SendAlertRecovery.RESTART, SendAlertError.AttemptNotFound.recovery())
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
}
