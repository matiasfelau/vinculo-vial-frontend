package ar.felaufernandezbottaro.vinculovial.presentation.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.VehicleCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los textos del contrato de US-14, palabra por palabra. */
class VehicleTextsTest {

    @Test
    fun `el resumen dice cuantos y nunca cuales`() {
        assertEquals("Tenés 1 de 3 vehículos vinculados", VehicleTexts.summary(VehicleCount(1, 3)))
        assertEquals("Tenés 0 de 3 vehículos vinculados", VehicleTexts.summary(VehicleCount(0, 3)))
    }

    @Test
    fun `el texto del tope usa el maximo del servidor`() {
        assertEquals("Podés tener hasta 3 vehículos", VehicleTexts.limit(3))
        assertEquals("Podés tener hasta 5 vehículos", VehicleTexts.limit(5))
    }

    @Test
    fun `las confirmaciones dicen la patente y que pasa con las alertas`() {
        assertEquals(
            "Vas a vincular AB123CD. Vas a recibir las alertas de esta patente",
            VehicleTexts.linkConfirmation("AB123CD"),
        )
        assertEquals(
            "Vas a desvincular AB123CD. Vas a dejar de recibir las alertas de esta patente",
            VehicleTexts.unlinkConfirmation("AB123CD"),
        )
        assertEquals(
            "Vas a dejar de recibir las alertas de todos tus vehículos",
            VehicleTexts.UNLINK_ALL_CONFIRMATION,
        )
    }

    @Test
    fun `cada code del backend tiene el texto del contrato`() {
        assertEquals("Ya tenés esta patente vinculada", VehicleError.Duplicate.message(3))
        assertEquals("Podés tener hasta 3 vehículos", VehicleError.LimitReached.message(3))
        assertEquals("Revisá el formato de la patente", VehicleError.InvalidPlate.message(3))
        assertEquals("No tenés esa patente vinculada", VehicleError.NotFound.message(3))
    }

    @Test
    fun `los demas errores tienen mensaje propio`() {
        val mensajes = listOf(
            VehicleError.Unavailable,
            VehicleError.Network,
            VehicleError.Configuration,
            VehicleError.Unexpected,
        ).map { it.message(3) }

        assertEquals(mensajes.size, mensajes.toSet().size)
        mensajes.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `los problemas de la patente escrita tienen mensaje`() {
        assertEquals("Ingresá la patente.", PlateProblem.REQUIRED.message())
        assertEquals("La patente tiene que ser AAA999 o AA999AA.", PlateProblem.FORMAT.message())
    }
}
