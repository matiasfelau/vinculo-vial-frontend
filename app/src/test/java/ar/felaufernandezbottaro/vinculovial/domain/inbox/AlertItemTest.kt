package ar.felaufernandezbottaro.vinculovial.domain.inbox

import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.inboxPreview
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** La fila de Recibidos: la pill "Ver fotos" y la última línea de ViVIAna, con sus excepciones. */
class AlertItemTest {

    private val item = AlertItem(
        id = "alerta-1",
        alertType = "CRASHED",
        createdAt = Instant.EPOCH,
        evidence = listOf(EvidenceRef("GENERAL", 0)),
    )

    private val reviewPrompt = "ViVIAna: Revisá las fotos antes de actuar."

    @Test
    fun `la pill de la fila invita a abrir el mensaje con Ver fotos`() {
        assertEquals("Ver fotos", ViviMessages.INBOX_VIEW_PHOTOS)
    }

    @Test
    fun `la ultima linea pide revisar las fotos antes de actuar`() {
        assertEquals(reviewPrompt, item.inboxPreview())
    }

    @Test
    fun `requires_review no cambia la fila`() {
        assertEquals(item.copy(requiresReview = false).inboxPreview(), item.copy(requiresReview = true).inboxPreview())
        assertEquals(reviewPrompt, item.copy(requiresReview = true).inboxPreview())
    }

    @Test
    fun `ya reportaste gana sobre la linea de ViVIAna`() {
        assertEquals("Reportaste al emisor.", item.copy(reported = true).inboxPreview())
    }

    @Test
    fun `ya valoraste gana sobre la linea de ViVIAna`() {
        assertEquals("Marcaste que el aviso te sirvió.", item.copy(myFeedback = FeedbackRating.UP).inboxPreview())
        assertEquals("Marcaste que el aviso no te sirvió.", item.copy(myFeedback = FeedbackRating.DOWN).inboxPreview())
    }

    @Test
    fun `sin evidencia la fila dice que ya no esta disponible`() {
        assertEquals("La evidencia ya no está disponible.", item.copy(evidence = emptyList()).inboxPreview())
    }

    @Test
    fun `haber reportado gana sobre haber valorado`() {
        assertEquals("Reportaste al emisor.", item.copy(reported = true, myFeedback = FeedbackRating.UP).inboxPreview())
    }

    @Test
    fun `lo que hizo el usuario gana tambien sobre la falta de evidencia`() {
        assertEquals(
            "Marcaste que el aviso te sirvió.",
            item.copy(evidence = emptyList(), myFeedback = FeedbackRating.UP).inboxPreview(),
        )
        assertEquals("Reportaste al emisor.", item.copy(evidence = emptyList(), reported = true).inboxPreview())
    }
}
