package ar.felaufernandezbottaro.vinculovial.domain.inbox

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** El estado que ve el emisor sale sólo de `status` y `seen_at`. */
class SentAlertTest {

    private val seenAt = Instant.parse("2026-10-15T13:41:00Z")

    private fun alert(status: SentAlertStatus, seenAt: Instant? = null) = SentAlert(
        id = "alerta-1",
        alertType = "CRASHED",
        createdAt = Instant.EPOCH,
        status = status,
        seenAt = seenAt,
    )

    @Test
    fun `PENDING_VALIDATION es en validacion`() {
        assertEquals(SentAlertState.IN_VALIDATION, alert(SentAlertStatus.PENDING_VALIDATION).state)
    }

    @Test
    fun `ACTIVE sin seen_at es validada`() {
        assertEquals(SentAlertState.VALIDATED, alert(SentAlertStatus.ACTIVE).state)
    }

    @Test
    fun `ACTIVE con seen_at es leida`() {
        assertEquals(SentAlertState.SEEN, alert(SentAlertStatus.ACTIVE, seenAt).state)
    }

    @Test
    fun `REJECTED es rechazada`() {
        assertEquals(SentAlertState.REJECTED, alert(SentAlertStatus.REJECTED).state)
    }

    @Test
    fun `EXPIRED es sin respuesta`() {
        assertEquals(SentAlertState.NO_RESPONSE, alert(SentAlertStatus.EXPIRED).state)
    }

    @Test
    fun `seen_at solo cuenta con la alerta ACTIVE`() {
        assertEquals(SentAlertState.IN_VALIDATION, alert(SentAlertStatus.PENDING_VALIDATION, seenAt).state)
        assertEquals(SentAlertState.REJECTED, alert(SentAlertStatus.REJECTED, seenAt).state)
        assertEquals(SentAlertState.NO_RESPONSE, alert(SentAlertStatus.EXPIRED, seenAt).state)
    }

    /** Anonimato: el modelo del emisor no tiene dónde guardar nada más que lo que el contrato permite. */
    @Test
    fun `el modelo solo tiene los campos permitidos al emisor`() {
        val fields = SentAlert::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") }.toSet()

        assertEquals(setOf("id", "alertType", "createdAt", "status", "seenAt"), fields)
    }
}
