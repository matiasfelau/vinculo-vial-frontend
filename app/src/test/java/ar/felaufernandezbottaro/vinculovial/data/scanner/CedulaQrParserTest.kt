package ar.felaufernandezbottaro.vinculovial.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El parser es el punto más frágil del pipeline de US-04: si el formato real del QR
 * termina siendo otro, se ajusta acá y en ningún otro lado.
 */
class CedulaQrParserTest {

    private val parser = CedulaQrParser()

    @Test
    fun `lee dni y patente con formato viejo`() {
        val cedula = parser.parse("30123456@ABC123")

        assertEquals("30123456", cedula?.dni)
        assertEquals("ABC123", cedula?.patente)
    }

    @Test
    fun `lee dni y patente con formato mercosur`() {
        val cedula = parser.parse("30123456@AB123CD")

        assertEquals("30123456", cedula?.dni)
        assertEquals("AB123CD", cedula?.patente)
    }

    @Test
    fun `normaliza la patente a mayusculas`() {
        val cedula = parser.parse("30123456@ab123cd")

        assertEquals("AB123CD", cedula?.patente)
    }

    @Test
    fun `conserva los ceros a la izquierda del dni`() {
        val cedula = parser.parse("01234567@ABC123")

        assertEquals("01234567", cedula?.dni)
    }

    @Test
    fun `rechaza un codigo de otro tipo`() {
        assertNull(parser.parse("https://ejemplo.com/algo"))
        assertNull(parser.parse(""))
        assertNull(parser.parse("@"))
    }

    @Test
    fun `rechaza si falta la patente`() {
        assertNull(parser.parse("30123456"))
    }

    @Test
    fun `rechaza si el dni no tiene forma de dni`() {
        assertNull(parser.parse("123@ABC123"))
    }

    @Test
    fun `rechaza si la patente no matchea ningun formato`() {
        assertNull(parser.parse("30123456@1234567"))
        assertNull(parser.parse("30123456@AB"))
    }

    @Test
    fun `rechaza campos de mas`() {
        assertNull(parser.parse("30123456@ABC123@extra"))
    }

    @Test
    fun `no expone los datos al renderizarse`() {
        val rendered = parser.parse("30123456@ABC123").toString()

        assertEquals(true, rendered.contains("***"))
        assertEquals(false, rendered.contains("30123456"))
        assertEquals(false, rendered.contains("ABC123"))
    }
}
