package ar.felaufernandezbottaro.vinculovial.presentation.vivi

import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceRef
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackRating
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Los textos de ViVIAna son plantillas de la app: se fijan acá, palabra por palabra. */
class ViviMessagesTest {

    private val item = AlertItem(
        id = "alerta-1",
        alertType = "CRASHED",
        createdAt = Instant.EPOCH,
        evidence = listOf(EvidenceRef("GENERAL", 0)),
    )

    // -- Sin variantes de confianza ------------------------------------------------------------

    @Test
    fun `todas las alertas cierran con Revisá las fotos antes de actuar`() {
        assertEquals("Revisá las fotos antes de actuar.", ViviMessages.OWNER_REVIEW_PROMPT)
    }

    // -- Chat del dueño ----------------------------------------------------------------------

    @Test
    fun `el saludo del dueño avisa que hay un mensaje sobre sus vehiculos`() {
        assertEquals(
            "Hola, soy ViVIAna. Tenés un nuevo mensaje sobre uno de tus vehículos.",
            ViviMessages.OWNER_GREETING,
        )
        assertTrue(ViviMessages.OWNER_GREETING.contains("Tenés un nuevo mensaje sobre uno de tus vehículos."))
    }

    @Test
    fun `el mensaje del tipo es la frase fija que eligio el emisor`() {
        assertEquals("Tu auto está bloqueando una rampa (garage o accesibilidad).", alertMessage("PARKED_ON_GARAGE"))
        assertEquals("Tu auto fue chocado.", alertMessage("CRASHED"))
    }

    @Test
    fun `un tipo desconocido cae en un mensaje generico, nunca rompe`() {
        assertEquals("Tenés un aviso sobre tu auto.", alertMessage("TIPO_NUEVO"))
        assertEquals("Tenés un aviso sobre tu auto", alertTitle("TIPO_NUEVO"))
    }

    @Test
    fun `el titulo de la fila es el mensaje sin el aclaratorio`() {
        assertEquals("Tu auto está bloqueando una rampa", alertTitle("PARKED_ON_GARAGE"))
        assertEquals("Tu auto fue chocado", alertTitle("CRASHED"))
    }

    @Test
    fun `una alerta historica de PARKED_TOO_CLOSE se sigue mostrando`() {
        assertEquals("Tu auto está estacionado muy cerca de otro", alertTitle("PARKED_TOO_CLOSE"))
        assertEquals("Tu auto está estacionado muy cerca de otro.", alertMessage("PARKED_TOO_CLOSE"))
    }

    @Test
    fun `la nota de la evidencia dice hasta cuando esta disponible`() {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")

        assertEquals(
            "Ocultamos rostros y otras patentes. Disponible hasta el 9 de noviembre.",
            evidenceNote(Instant.parse("2026-11-09T13:00:00Z"), zone),
        )
    }

    @Test
    fun `sin fecha de vencimiento la nota dice solo lo que se oculto`() {
        assertEquals("Ocultamos rostros y otras patentes.", evidenceNote(null))
    }

    @Test
    fun `el placeholder del 410 es La evidencia ya no está disponible`() {
        assertEquals("La evidencia ya no está disponible", ViviMessages.EVIDENCE_UNAVAILABLE)
    }

    @Test
    fun `las etiquetas de las fotos llevan numero solo si hay varias del mismo rol`() {
        assertEquals("General", evidenceTileLabel("GENERAL", 0, 1))
        assertEquals("Patente", evidenceTileLabel("PLATE", 0, 1))
        assertEquals("Contexto", evidenceTileLabel("CONTEXT", 0, 1))
        assertEquals("Daño", evidenceTileLabel("DETAIL", 0, 1))
        assertEquals("Daño 1", evidenceTileLabel("DETAIL", 0, 2))
        assertEquals("Daño 3", evidenceTileLabel("DETAIL", 2, 3))
        assertEquals("Foto", evidenceTileLabel("ROL_NUEVO", 0, 1))
    }

    // -- Pestaña Enviados --------------------------------------------------------------------

    @Test
    fun `Enviados es una pantalla en construccion sin datos`() {
        assertEquals("En construcción", ViviMessages.SENT_UNDER_CONSTRUCTION_TITLE)
        assertEquals(
            "Pronto vas a ver acá tus alertas enviadas, si fueron validadas y cuándo las leyeron.",
            ViviMessages.SENT_UNDER_CONSTRUCTION_BODY,
        )
    }

    // -- Chat del emisor ---------------------------------------------------------------------

    @Test
    fun `los mensajes que elige el emisor son fijos`() {
        assertEquals("Tu auto está bloqueando una rampa (garage o accesibilidad).", AlertType.PARKED_ON_GARAGE.senderMessage())
        assertEquals("Tu auto fue chocado.", AlertType.CRASHED.senderMessage())
    }

    @Test
    fun `la nota explica por que los mensajes son fijos`() {
        assertEquals("Los mensajes son fijos para que el dueño no pueda identificarte.", ViviMessages.SENDER_FIXED_NOTE)
    }

    @Test
    fun `la intro de la captura pide las fotos segun el tipo`() {
        assertTrue(ViviMessages.senderPhotosIntro(AlertType.CRASHED).contains("el daño"))
        assertTrue(ViviMessages.senderPhotosIntro(AlertType.PARKED_ON_GARAGE).contains("desde la vereda"))
        assertFalse(ViviMessages.senderPhotosIntro(AlertType.PARKED_ON_GARAGE).contains("el daño"))
    }

    @Test
    fun `el resumen del adjunto cuenta las fotos y dice que estan cifradas`() {
        assertEquals("3 fotos cifradas adjuntas", ViviMessages.senderAttachmentSummary(3))
        assertEquals("5 fotos cifradas adjuntas", ViviMessages.senderAttachmentSummary(5))
        assertEquals("1 foto cifrada adjunta", ViviMessages.senderAttachmentSummary(1))
    }

    @Test
    fun `el boton que abre la camara guiada se llama Tomar fotos`() {
        assertEquals("Tomar fotos", ViviMessages.SENDER_TAKE_PHOTOS)
    }

    @Test
    fun `tras el 202 ViVIAna confirma la recepcion sin prometerle nada al emisor`() {
        val receipt = ViviMessages.SENDER_SENT_RECEIPT

        assertEquals("Recibí todo. El sistema va a validar la información y, si la aprueba, le aviso al dueño.", receipt)
        assertFalse(receipt.contains("te aviso", ignoreCase = true))
        assertFalse(receipt.contains("te escribo", ignoreCase = true))
    }

    @Test
    fun `el envio incierto dice que puede haberse enviado`() {
        assertEquals("No pudimos confirmar el envío. Puede que tu alerta se haya enviado.", ViviMessages.SENDER_POSSIBLY_SENT)
    }

    @Test
    fun `el avatar y el nombre son los de ViVIAna`() {
        assertEquals("ViVIAna", ViviMessages.NAME)
        assertEquals("Asistente de Vínculo Vial", ViviMessages.TAGLINE)
    }
}
