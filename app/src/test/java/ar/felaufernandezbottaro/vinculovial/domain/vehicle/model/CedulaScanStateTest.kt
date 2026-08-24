package ar.felaufernandezbottaro.vinculovial.domain.vehicle.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CedulaScanStateTest {

    private val cedula = CedulaData(chasis = "APJ56565", patente = "GUQ856", nroControl = "5092160")

    @Test
    fun `esta listo solo con el codigo decodificado y la cedula presente`() {
        val state = CedulaScanState(stage = CedulaScanStage.DECODED, cedula = cedula)

        assertTrue(state.isDone)
    }

    @Test
    fun `DECODED sin cedula no cuenta como listo`() {
        val state = CedulaScanState(stage = CedulaScanStage.DECODED, cedula = null)

        assertFalse(state.isDone)
    }

    @Test
    fun `ningun estado intermedio deja avanzar`() {
        listOf(CedulaScanStage.SEARCHING, CedulaScanStage.NOT_A_CEDULA).forEach { stage ->
            assertFalse(
                "$stage no debería dejar avanzar",
                CedulaScanState(stage = stage, cedula = cedula).isDone,
            )
        }
    }

    @Test
    fun `el estado inicial esta buscando y no deja avanzar`() {
        val inicial = CedulaScanState()

        assertTrue(inicial.stage == CedulaScanStage.SEARCHING)
        assertFalse(inicial.isDone)
    }
}
