package ar.felaufernandezbottaro.vinculovial.domain.scan

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fija el contrato exacto de `alert_type` contra `vinculo-vial-alert`: el backend bindea el
 * JSON contra estas constantes UPPER_SNAKE_CASE, no contra [AlertType.label] (la frase en
 * español que ve el usuario). Un 400 real del backend fue el síntoma de que ambos valores
 * estaban unificados en un único campo -- este test evita que vuelvan a mezclarse.
 */
class AlertTypeTest {

    @Test
    fun `wireValue coincide exacto con el enum del backend`() {
        assertEquals("PARKED_ON_GARAGE", AlertType.PARKED_ON_GARAGE.wireValue)
        assertEquals("PARKED_TOO_CLOSE", AlertType.PARKED_TOO_CLOSE.wireValue)
        assertEquals("CRASHED", AlertType.CRASHED.wireValue)
    }

    @Test
    fun `label es la frase en espanol visible en el chat, distinta del wireValue`() {
        AlertType.entries.forEach { alertType ->
            assertEquals(false, alertType.label == alertType.wireValue)
        }
    }

    @Test
    fun `el selector solo ofrece los tipos con validacion de IA`() {
        assertEquals(listOf(AlertType.PARKED_ON_GARAGE, AlertType.CRASHED), AlertType.offered)
    }

    @Test
    fun `PARKED_TOO_CLOSE no se ofrece pero sigue en el enum para el historial`() {
        assertEquals(false, AlertType.PARKED_TOO_CLOSE.isOffered)
        assertEquals(false, AlertType.PARKED_TOO_CLOSE in AlertType.offered)
        assertEquals("PARKED_TOO_CLOSE", AlertType.PARKED_TOO_CLOSE.wireValue)
    }

    @Test
    fun `el label de PARKED_ON_GARAGE habla de rampa de garage o de accesibilidad`() {
        assertEquals(
            "Estacionado frente a una rampa (garage o de accesibilidad)",
            AlertType.PARKED_ON_GARAGE.label,
        )
    }
}
