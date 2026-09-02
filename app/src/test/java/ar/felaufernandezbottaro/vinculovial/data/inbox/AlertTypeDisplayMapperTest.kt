package ar.felaufernandezbottaro.vinculovial.data.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlertTypeDisplayMapperTest {

    @Test
    fun `LUCES_ENCENDIDAS mapea a su icono y etiqueta`() {
        val display = mapAlertTypeToDisplay("LUCES_ENCENDIDAS")

        assertEquals(AlertTypeIconKind.LUCES_ENCENDIDAS, display.icon)
        assertEquals("Luces encendidas", display.label)
    }

    @Test
    fun `PARKED_ON_GARAGE mapea a su icono y etiqueta`() {
        val display = mapAlertTypeToDisplay("PARKED_ON_GARAGE")

        assertEquals(AlertTypeIconKind.PARKED_ON_GARAGE, display.icon)
        assertEquals("Estacionado sobre un garaje", display.label)
    }

    @Test
    fun `PARKED_TOO_CLOSE mapea a su icono y etiqueta`() {
        val display = mapAlertTypeToDisplay("PARKED_TOO_CLOSE")

        assertEquals(AlertTypeIconKind.PARKED_TOO_CLOSE, display.icon)
        assertEquals("Estacionado muy cerca de otro auto", display.label)
    }

    @Test
    fun `CRASHED mapea a su icono y etiqueta`() {
        val display = mapAlertTypeToDisplay("CRASHED")

        assertEquals(AlertTypeIconKind.CRASHED, display.icon)
        assertEquals("Choque", display.label)
    }

    /**
     * Requisito central: un `alert_type` que esta app todavía no conoce (un tipo nuevo que el
     * backend agregó después de este release) nunca lanza una excepción -- cae en un ícono
     * genérico y una etiqueta neutra, degradado pero renderizable.
     */
    @Test
    fun `un alert_type desconocido no lanza excepcion y usa el icono generico`() {
        val display = mapAlertTypeToDisplay("UN_TIPO_QUE_NO_EXISTE_TODAVIA")

        assertEquals(AlertTypeIconKind.GENERIC, display.icon)
        assertEquals("Incidente reportado", display.label)
    }

    @Test
    fun `un string vacio tambien cae en el icono generico`() {
        val display = mapAlertTypeToDisplay("")

        assertEquals(AlertTypeIconKind.GENERIC, display.icon)
    }

    @Test
    fun `cada tipo conocido tiene una etiqueta distinta`() {
        val labels = listOf("LUCES_ENCENDIDAS", "PARKED_ON_GARAGE", "PARKED_TOO_CLOSE", "CRASHED")
            .map { mapAlertTypeToDisplay(it).label }

        assertEquals(labels.size, labels.toSet().size)
        assertNotEquals(mapAlertTypeToDisplay("otro").label, labels[0])
    }
}
