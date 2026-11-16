package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AlertDateFormatterTest {

    private val zoneAr = ZoneId.of("America/Argentina/Buenos_Aires")

    @Test
    fun `hace menos de un minuto`() {
        val now = Instant.parse("2026-08-18T10:00:30Z")
        val createdAt = Instant.parse("2026-08-18T10:00:00Z")

        assertEquals("Hace instantes", createdAt.toFriendlyAlertDate(now, zoneAr))
    }

    @Test
    fun `hace minutos, mismo dia`() {
        val now = Instant.parse("2026-08-18T10:35:00Z")
        val createdAt = Instant.parse("2026-08-18T10:00:00Z")

        assertEquals("Hace 35 min", createdAt.toFriendlyAlertDate(now, zoneAr))
    }

    @Test
    fun `hace exactamente 1 hora usa singular`() {
        val now = Instant.parse("2026-08-18T11:00:00Z")
        val createdAt = Instant.parse("2026-08-18T10:00:00Z")

        assertEquals("Hace 1 hora", createdAt.toFriendlyAlertDate(now, zoneAr))
    }

    @Test
    fun `hace varias horas, mismo dia calendario`() {
        val now = Instant.parse("2026-08-18T15:00:00Z")
        val createdAt = Instant.parse("2026-08-18T10:00:00Z")

        assertEquals("Hace 5 horas", createdAt.toFriendlyAlertDate(now, zoneAr))
    }

    /**
     * Caso obligatorio de CLAUDE.md: cambio de día calendario, aunque hayan pasado pocos
     * minutos reales -- 23:50 (zona AR, UTC-3) de ayer sigue siendo "ayer" a las 00:10 de hoy.
     */
    @Test
    fun `cambio de dia calendario aunque hayan pasado pocos minutos`() {
        val now = Instant.parse("2026-08-19T03:10:00Z")
        val createdAt = Instant.parse("2026-08-19T02:50:00Z")

        assertEquals("Ayer 23:50", createdAt.toFriendlyAlertDate(now, zoneAr))
    }

    @Test
    fun `ayer con hora especifica`() {
        val now = Instant.parse("2026-08-19T15:00:00Z")
        val createdAt = Instant.parse("2026-08-18T17:30:00Z")

        assertEquals("Ayer 14:30", createdAt.toFriendlyAlertDate(now, zoneAr))
    }

    @Test
    fun `mas de un dia atras usa fecha corta`() {
        val now = Instant.parse("2026-08-25T12:00:00Z")
        val createdAt = Instant.parse("2026-08-18T13:00:00Z")

        assertEquals("18 ago", createdAt.toFriendlyAlertDate(now, zoneAr))
    }

    /** El mismo instante se presenta distinto según la zona horaria del dispositivo. */
    @Test
    fun `la misma marca de tiempo se formatea distinto segun la zona`() {
        val now = Instant.parse("2026-08-19T02:00:00Z")
        val createdAt = Instant.parse("2026-08-18T23:00:00Z")

        val enArgentina = createdAt.toFriendlyAlertDate(now, ZoneId.of("America/Argentina/Buenos_Aires"))
        val enUtc = createdAt.toFriendlyAlertDate(now, ZoneId.of("UTC"))

        assertEquals("Hace 3 horas", enArgentina)
        assertEquals("Ayer 23:00", enUtc)
    }

    @Test
    fun `en zona UTC+ un mismo instante puede caer al dia siguiente`() {
        val zoneTokyo = ZoneId.of("Asia/Tokyo")
        val now = Instant.parse("2026-08-18T16:10:00Z")
        val createdAt = Instant.parse("2026-08-18T15:50:00Z")

        assertEquals("Hace 20 min", createdAt.toFriendlyAlertDate(now, zoneTokyo))
    }

    // -- Hora de la fila del buzón, como en un chat ------------------------------------------

    @Test
    fun `la fila de hoy muestra la hora`() {
        val now = Instant.parse("2026-10-09T18:00:00Z")
        val createdAt = Instant.parse("2026-10-09T13:42:00Z")

        assertEquals("10:42", createdAt.toInboxListTime(now, zoneAr))
    }

    @Test
    fun `la fila de ayer dice Ayer`() {
        val now = Instant.parse("2026-10-09T18:00:00Z")
        val createdAt = Instant.parse("2026-10-08T13:42:00Z")

        assertEquals("Ayer", createdAt.toInboxListTime(now, zoneAr))
    }

    @Test
    fun `una fila mas vieja muestra la fecha corta sin punto`() {
        val now = Instant.parse("2026-10-09T18:00:00Z")
        val createdAt = Instant.parse("2026-10-03T13:42:00Z")

        assertEquals("3 oct", createdAt.toInboxListTime(now, zoneAr))
    }

    @Test
    fun `el cambio de dia calendario cuenta como ayer aunque hayan pasado pocos minutos`() {
        val now = Instant.parse("2026-10-09T03:10:00Z")
        val createdAt = Instant.parse("2026-10-09T02:50:00Z")

        assertEquals("Ayer", createdAt.toInboxListTime(now, zoneAr))
    }

    // -- Sello de dia de la conversacion ------------------------------------------------------

    @Test
    fun `el sello de hoy es Hoy y la hora`() {
        val now = Instant.parse("2026-10-09T18:00:00Z")

        assertEquals("Hoy 10:42", Instant.parse("2026-10-09T13:42:00Z").toChatDayStamp(now, zoneAr))
    }

    @Test
    fun `el sello de ayer es Ayer y la hora`() {
        val now = Instant.parse("2026-10-09T18:00:00Z")

        assertEquals("Ayer 10:42", Instant.parse("2026-10-08T13:42:00Z").toChatDayStamp(now, zoneAr))
    }

    @Test
    fun `el sello de un dia anterior es la fecha corta y la hora`() {
        val now = Instant.parse("2026-10-09T18:00:00Z")

        assertEquals("3 oct 10:42", Instant.parse("2026-10-03T13:42:00Z").toChatDayStamp(now, zoneAr))
    }

    // -- Fecha larga --------------------------------------------------------------------------

    @Test
    fun `la hora del dia es HH mm en la zona pedida`() {
        assertEquals("10:05", Instant.parse("2026-11-09T13:05:00Z").toClockTime(zoneAr))
        assertEquals("00:00", Instant.parse("2026-11-09T03:00:00Z").toClockTime(zoneAr))
    }

    @Test
    fun `la fecha larga es dia de mes en espanol`() {
        assertEquals("9 de noviembre", Instant.parse("2026-11-09T13:00:00Z").toLongDate(zoneAr))
        assertEquals("1 de enero", Instant.parse("2027-01-01T13:00:00Z").toLongDate(zoneAr))
    }
}
