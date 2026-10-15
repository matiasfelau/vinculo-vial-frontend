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
    fun `la captura pide exactamente una foto por paso, tambien el daño`() {
        val steps = EvidencePlan.stepsFor(AlertType.CRASHED)

        assertEquals(listOf(1, 1, 1), steps.map { it.maxPhotos })
    }

    @Test
    fun `el contrato sigue aceptando de 1 a 3 fotos de daño y una de los demas roles`() {
        assertEquals(3, EvidencePlan.contractMaxPhotos(EvidenceRole.DETAIL))
        assertEquals(1, EvidencePlan.contractMaxPhotos(EvidenceRole.GENERAL))
        assertEquals(1, EvidencePlan.contractMaxPhotos(EvidenceRole.PLATE))
        assertEquals(1, EvidencePlan.contractMaxPhotos(EvidenceRole.CONTEXT))
    }

    @Test
    fun `la guia de la foto del daño pide no sacarla demasiado cerca`() {
        val detail = EvidencePlan.stepsFor(AlertType.CRASHED).single { it.role == EvidenceRole.DETAIL }

        assertEquals("Sacá una foto del daño. No la saques demasiado cerca.", detail.instruction)
    }

    @Test
    fun `PARKED_ON_GARAGE pide general, patente y contexto`() {
        val steps = EvidencePlan.stepsFor(AlertType.PARKED_ON_GARAGE)

        assertEquals(listOf(EvidenceRole.GENERAL, EvidenceRole.PLATE, EvidenceRole.CONTEXT), steps.map { it.role })
        assertTrue(steps.all { it.maxPhotos == 1 })
    }

    @Test
    fun `la guia de la foto de contexto pide no sacarla demasiado cerca`() {
        val context = EvidencePlan.stepsFor(AlertType.PARKED_ON_GARAGE).single { it.role == EvidenceRole.CONTEXT }

        assertEquals(
            "Sacá la foto desde la vereda. No la saques demasiado cerca: tienen que verse el auto y la rampa.",
            context.instruction,
        )
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
