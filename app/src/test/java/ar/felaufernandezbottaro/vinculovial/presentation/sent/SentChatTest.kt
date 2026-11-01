package ar.felaufernandezbottaro.vinculovial.presentation.sent

import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlert
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlertStatus
import ar.felaufernandezbottaro.vinculovial.presentation.scan.SenderChatItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** El chat del emisor se reconstruye sólo desde el estado, con los textos del contrato. */
class SentChatTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-15T20:00:00Z")

    private fun alert(
        status: SentAlertStatus,
        seenAt: Instant? = null,
        alertType: String = "CRASHED",
    ) = SentAlert(
        id = "alerta-1",
        alertType = alertType,
        createdAt = Instant.parse("2026-10-15T10:00:00Z"),
        status = status,
        seenAt = seenAt,
    )

    private fun items(alert: SentAlert) = sentChatItems(alert, now = now, zone = zone)

    private val receipt = SenderChatItem.Vivi(
        "Recibí todo. El sistema va a validar la información y, si la aprueba, la dejo en el buzón de esa patente.",
    )

    @Test
    fun `en validacion solo esta el mensaje elegido y el recibido`() {
        assertEquals(
            listOf(SenderChatItem.User("Tu auto fue chocado."), receipt),
            items(alert(SentAlertStatus.PENDING_VALIDATION)),
        )
    }

    @Test
    fun `el mensaje del emisor es el fijo del tipo`() {
        val garage = items(alert(SentAlertStatus.PENDING_VALIDATION, alertType = "PARKED_ON_GARAGE"))

        assertEquals(
            SenderChatItem.User("Tu auto está bloqueando una rampa (garage o accesibilidad)."),
            garage.first(),
        )
    }

    @Test
    fun `validada agrega que ViVIAna la dejo en el buzon de esa patente`() {
        assertEquals(
            listOf(
                SenderChatItem.User("Tu auto fue chocado."),
                receipt,
                SenderChatItem.Vivi("Validé tu alerta y la dejé en el buzón de esa patente."),
            ),
            items(alert(SentAlertStatus.ACTIVE)),
        )
    }

    @Test
    fun `validada dice lo mismo para todas las alertas, sin importar el tipo`() {
        val messages = listOf("CRASHED", "PARKED_ON_GARAGE", "PARKED_TOO_CLOSE", "TIPO_NUEVO").map { type ->
            (items(alert(SentAlertStatus.ACTIVE, alertType = type)).last() as SenderChatItem.Vivi).text
        }

        assertEquals(setOf("Validé tu alerta y la dejé en el buzón de esa patente."), messages.toSet())
    }

    @Test
    fun `leida suma que el dueño leyo el mensaje con la hora de seen_at`() {
        val result = items(alert(SentAlertStatus.ACTIVE, seenAt = Instant.parse("2026-10-15T13:41:00Z")))

        assertEquals(
            listOf(
                SenderChatItem.User("Tu auto fue chocado."),
                receipt,
                SenderChatItem.Vivi("Validé tu alerta y la dejé en el buzón de esa patente."),
                SenderChatItem.Vivi("El dueño leyó tu mensaje.", time = "Hoy 13:41"),
            ),
            result,
        )
    }

    @Test
    fun `la hora de lectura usa el dia si no es de hoy`() {
        val yesterday = items(alert(SentAlertStatus.ACTIVE, seenAt = Instant.parse("2026-10-14T09:05:00Z")))
        val older = items(alert(SentAlertStatus.ACTIVE, seenAt = Instant.parse("2026-10-03T09:05:00Z")))

        assertEquals("Ayer 09:05", (yesterday.last() as SenderChatItem.Vivi).time)
        assertEquals("3 oct 09:05", (older.last() as SenderChatItem.Vivi).time)
    }

    @Test
    fun `rechazada dice el mensaje generico, sin motivo`() {
        val result = items(alert(SentAlertStatus.REJECTED))

        assertEquals(SenderChatItem.Vivi("No pudimos validar tu alerta.", isWarning = true), result.last())
        assertEquals(3, result.size)
    }

    @Test
    fun `vencida dice que no tuvo respuesta`() {
        assertEquals(
            SenderChatItem.Vivi("Tu alerta no tuvo respuesta.", isWarning = true),
            items(alert(SentAlertStatus.EXPIRED)).last(),
        )
    }

    @Test
    fun `un tipo que la app no conoce omite el mensaje del emisor y sigue`() {
        val result = items(alert(SentAlertStatus.PENDING_VALIDATION, alertType = "TIPO_NUEVO"))

        assertEquals(listOf(receipt), result)
    }

    /** Anonimato: el chat del emisor nunca muestra fotos, veredicto, motivo ni datos del dueño. */
    @Test
    fun `el chat del emisor no tiene adjuntos ni indicadores de escritura`() {
        SentAlertStatus.entries.forEach { status ->
            val all = items(alert(status, seenAt = if (status == SentAlertStatus.ACTIVE) now else null))

            assertTrue(all.none { it is SenderChatItem.Attachment || it is SenderChatItem.Typing })
        }
    }

    @Test
    fun `ningun texto del chat del emisor menciona fraude, motivo ni reputacion`() {
        val texts = SentAlertStatus.entries.flatMap { status ->
            items(alert(status, seenAt = if (status == SentAlertStatus.ACTIVE) now else null))
        }.filterIsInstance<SenderChatItem.Vivi>().map { it.text.lowercase() }

        listOf("fraude", "reputaci", "motivo", "veredicto", "no está en la red", "no registrada").forEach { forbidden ->
            assertTrue("'$forbidden' no debería aparecer", texts.none { forbidden in it })
        }
    }
}
