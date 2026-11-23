package ar.felaufernandezbottaro.vinculovial.domain.evidence

import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidencePlanTest {

    @Test
    fun `CRASHED pide general y patente`() {
        val steps = EvidencePlan.stepsFor(AlertType.CRASHED)

        assertEquals(listOf(EvidenceRole.GENERAL, EvidenceRole.PLATE), steps.map { it.role })
    }

    @Test
    fun `PARKED_ON_GARAGE pide general y patente`() {
        val steps = EvidencePlan.stepsFor(AlertType.PARKED_ON_GARAGE)

        assertEquals(listOf(EvidenceRole.GENERAL, EvidenceRole.PLATE), steps.map { it.role })
    }

    @Test
    fun `todo tipo ofrecido pide exactamente dos fotos, una por paso`() {
        AlertType.offered.forEach { alertType ->
            val steps = EvidencePlan.stepsFor(alertType)

            assertEquals("$alertType", listOf(1, 1), steps.map { it.maxPhotos })
        }
    }

    @Test
    fun `los dos tipos comparten el mismo plan`() {
        assertEquals(EvidencePlan.stepsFor(AlertType.CRASHED), EvidencePlan.stepsFor(AlertType.PARKED_ON_GARAGE))
    }

    @Test
    fun `el unico vocabulario de roles es general y patente`() {
        assertEquals(listOf("GENERAL", "PLATE"), EvidenceRole.entries.map { it.name })
    }

    @Test
    fun `las instrucciones guian el auto completo y el primer plano de la patente`() {
        val steps = EvidencePlan.stepsFor(AlertType.CRASHED)

        assertEquals("Sacá una foto del auto completo, con la patente visible", steps[0].instruction)
        assertEquals("Sacá un primer plano de la patente", steps[1].instruction)
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
        }
    }
}
