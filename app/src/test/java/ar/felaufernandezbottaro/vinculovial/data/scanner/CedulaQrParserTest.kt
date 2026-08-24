package ar.felaufernandezbottaro.vinculovial.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El parser es el punto más frágil del pipeline de US-04: si el formato real del QR
 * termina difiriendo en algún caso no visto, se ajusta acá y en ningún otro lado.
 *
 * El contenido de referencia (`raw()`) es el que se leyó de una cédula física real.
 */
class CedulaQrParserTest {

    private val parser = CedulaQrParser()

    /** Chasis, patente y número de control, tal como los separa el QR real -- con `\n`. */
    private fun raw(chasis: String = "APJ56565", patente: String = "GUQ856", control: String = "5092160") =
        "$chasis\n$patente\n$control"

    @Test
    fun `lee chasis patente y numero de control en el orden real del QR`() {
        val cedula = parser.parse(raw())

        assertEquals("APJ56565", cedula?.chasis)
        assertEquals("GUQ856", cedula?.patente)
        assertEquals("5092160", cedula?.nroControl)
    }

    @Test
    fun `tambien lee el contenido si viene con CRLF`() {
        val cedula = parser.parse("APJ56565\r\nGUQ856\r\n5092160")

        assertEquals("APJ56565", cedula?.chasis)
        assertEquals("GUQ856", cedula?.patente)
        assertEquals("5092160", cedula?.nroControl)
    }

    @Test
    fun `ignora espacios y lineas en blanco alrededor de cada campo`() {
        val cedula = parser.parse("\n  APJ56565 \n GUQ856  \n 5092160 \n\n")

        assertEquals("APJ56565", cedula?.chasis)
        assertEquals("GUQ856", cedula?.patente)
        assertEquals("5092160", cedula?.nroControl)
    }

    @Test
    fun `normaliza chasis y patente a mayusculas`() {
        val cedula = parser.parse(raw(chasis = "apj56565", patente = "guq856"))

        assertEquals("APJ56565", cedula?.chasis)
        assertEquals("GUQ856", cedula?.patente)
    }

    @Test
    fun `acepta patente en formato mercosur`() {
        val cedula = parser.parse(raw(patente = "AB123CD"))

        assertEquals("AB123CD", cedula?.patente)
    }

    @Test
    fun `rechaza un codigo de otro tipo`() {
        assertNull(parser.parse("https://ejemplo.com/algo"))
        assertNull(parser.parse(""))
        assertNull(parser.parse("\n\n"))
    }

    @Test
    fun `rechaza si falta alguna linea`() {
        assertNull(parser.parse("APJ56565\nGUQ856"))
    }

    @Test
    fun `rechaza lineas de mas`() {
        assertNull(parser.parse("APJ56565\nGUQ856\n5092160\nextra"))
    }

    @Test
    fun `rechaza si la patente no matchea ningun formato`() {
        assertNull(parser.parse(raw(patente = "1234567")))
        assertNull(parser.parse(raw(patente = "AB")))
    }

    @Test
    fun `rechaza si el numero de control no es numerico`() {
        assertNull(parser.parse(raw(control = "ABC1234")))
    }

    @Test
    fun `rechaza si el chasis tiene caracteres invalidos`() {
        assertNull(parser.parse(raw(chasis = "APJ-56565")))
    }

    @Test
    fun `no expone los datos al renderizarse`() {
        val rendered = parser.parse(raw()).toString()

        assertEquals(true, rendered.contains("***"))
        assertEquals(false, rendered.contains("APJ56565"))
        assertEquals(false, rendered.contains("GUQ856"))
        assertEquals(false, rendered.contains("5092160"))
    }
}
