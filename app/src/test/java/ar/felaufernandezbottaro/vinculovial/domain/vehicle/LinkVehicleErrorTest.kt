package ar.felaufernandezbottaro.vinculovial.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `isTransient` decide si la pantalla ofrece "Reintentar" o sólo "Entendido": reintentar sin
 * que el usuario cambie nada sólo sirve ante un fallo que puede resolverse solo -- 502/503/504
 * (`Unavailable`, incluido el circuito abierto) o de transporte (`Network`). Un 500 o un 4xx
 * volverían a fallar igual.
 */
class LinkVehicleErrorTest {

    /**
     * `when` exhaustivo sin `else`: al agregar un caso nuevo a `LinkVehicleError` este archivo
     * deja de compilar hasta que alguien decida a conciencia si es transitorio o no.
     */
    private fun expectedTransient(error: LinkVehicleError): Boolean = when (error) {
        LinkVehicleError.Unavailable,
        LinkVehicleError.Network,
        -> true

        LinkVehicleError.OwnerMismatch,
        LinkVehicleError.AlreadyLinked,
        LinkVehicleError.InvalidData,
        LinkVehicleError.Configuration,
        LinkVehicleError.Unexpected,
        -> false
    }

    private val every = listOf(
        LinkVehicleError.OwnerMismatch,
        LinkVehicleError.AlreadyLinked,
        LinkVehicleError.InvalidData,
        LinkVehicleError.Configuration,
        LinkVehicleError.Unavailable,
        LinkVehicleError.Network,
        LinkVehicleError.Unexpected,
    )

    @Test
    fun `isTransient es true solo para Unavailable y Network`() {
        every.forEach { error ->
            assertEquals("isTransient de $error", expectedTransient(error), error.isTransient)
        }
        assertEquals(
            setOf(LinkVehicleError.Unavailable, LinkVehicleError.Network),
            every.filter { it.isTransient }.toSet(),
        )
    }
}
