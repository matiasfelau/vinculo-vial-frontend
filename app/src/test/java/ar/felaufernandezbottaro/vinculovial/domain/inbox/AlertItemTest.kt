package ar.felaufernandezbottaro.vinculovial.domain.inbox

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AlertItemTest {

    private val item = AlertItem(id = "a", alertType = "CRASHED", createdAt = Instant.EPOCH)

    @Test
    fun `requires_review en false es confianza alta`() {
        assertEquals(AlertConfidence.HIGH, item.copy(requiresReview = false).confidence)
    }

    @Test
    fun `requires_review en true es confianza media`() {
        assertEquals(AlertConfidence.MEDIUM, item.copy(requiresReview = true).confidence)
    }

    @Test
    fun `por defecto un item es de confianza alta`() {
        assertEquals(AlertConfidence.HIGH, item.confidence)
    }
}
