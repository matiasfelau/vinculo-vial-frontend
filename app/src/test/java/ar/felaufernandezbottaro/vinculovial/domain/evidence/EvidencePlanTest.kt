package ar.felaufernandezbottaro.vinculovial.domain.evidence

import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidencePlanTest {

    @Test
    fun `CRASHED pide general, patente y daño`() {
        val steps = EvidencePlan.stepsFor(AlertType.CRASHED)

        assertEquals(listOf(EvidenceRole.GENERAL, EvidenceRole.PLATE, EvidenceRole.DETAIL), steps.map { it.role })
    }

    @Test
    fun `el daño admite hasta 3 fotos y los demas pasos una sola`() {
        val steps = EvidencePlan.stepsFor(AlertType.CRASHED)

        assertEquals(listOf(1, 1, 3), steps.map { it.maxPhotos })
    }

    @Test
    fun `PARKED_ON_GARAGE pide general, patente y contexto`() {
        val steps = EvidencePlan.stepsFor(AlertType.PARKED_ON_GARAGE)

        assertEquals(listOf(EvidenceRole.GENERAL, EvidenceRole.PLATE, EvidenceRole.CONTEXT), steps.map { it.role })
        assertTrue(steps.all { it.maxPhotos == 1 })
    }

    @Test
    fun `solo el paso de contexto tiene el encuadre guiado del rebaje y la rueda`() {
        val steps = EvidencePlan.stepsFor(AlertType.PARKED_ON_GARAGE)

        assertEquals(listOf(null, null, "Que entren el rebaje y la rueda más cercana"), steps.map { it.overlayHint })
        assertTrue(EvidencePlan.stepsFor(AlertType.CRASHED).all { it.overlayHint == null })
    }

    @Test
    fun `un tipo deshabilitado no tiene captura guiada`() {
        assertThrows(IllegalArgumentException::class.java) { EvidencePlan.stepsFor(AlertType.PARKED_TOO_CLOSE) }
    }

    @Test
    fun `todo tipo ofrecido tiene captura guiada con instrucciones`() {
        AlertType.offered.forEach { alertType ->
            val steps = EvidencePlan.stepsFor(alertType)
            assertFalse(steps.isEmpty())
            assertTrue(steps.all { it.instruction.isNotBlank() })
            assertNotNull(steps.first { it.role == EvidenceRole.GENERAL })
        }
    }
}
