package com.vinculovial.app.data.edge.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El formato real del QR del DNI electrónico no está confirmado (ver KDoc de
 * [ArgentineDniQrParser]), así que estos tests cubren las tres formas que se intentan,
 * no "el" formato correcto. Hay que sumar un caso con el contenido real apenas se pueda
 * escanear una tarjeta física.
 */
class ArgentineDniQrParserTest {

    @Test
    fun `reconoce el mismo formato delimitado del pdf417, por si se reutiliza`() {
        val raw = "00307513148@GARCIA@JUAN CARLOS@M@12345678@A@27/01/1990@04/03/2016"

        assertEquals("12345678", ArgentineDniQrParser.parseDniNumber(raw))
    }

    @Test
    fun `reconoce json con la clave dni`() {
        assertEquals("12345678", ArgentineDniQrParser.parseDniNumber("""{"dni":"12345678"}"""))
    }

    @Test
    fun `reconoce json con variantes de nombre de clave`() {
        listOf(
            """{"nroDocumento":"12345678"}""",
            """{"numero_documento":"12345678"}""",
            """{"NumDoc":"12345678"}""",
        ).forEach { raw ->
            assertEquals("caso: $raw", "12345678", ArgentineDniQrParser.parseDniNumber(raw))
        }
    }

    @Test
    fun `en json ignora otros campos numericos que no sean el documento`() {
        val raw = """{"tramite":"307513148","dni":"12345678","cuil":"20123456789"}"""

        assertEquals("12345678", ArgentineDniQrParser.parseDniNumber(raw))
    }

    @Test
    fun `reconoce el numero como query param de una url de verificacion`() {
        val raw = "https://verificar.renaper.gob.ar/dni?documento=12345678&tramite=307513148"

        assertEquals("12345678", ArgentineDniQrParser.parseDniNumber(raw))
    }

    @Test
    fun `no confunde el numero de tramite de la url con el documento`() {
        val raw = "https://verificar.renaper.gob.ar/dni?tramite=307513148&dni=12345678"

        assertEquals("12345678", ArgentineDniQrParser.parseDniNumber(raw))
    }

    @Test
    fun `contenido no reconocible devuelve null en vez de adivinar`() {
        listOf(
            "",
            "un texto cualquiera sin estructura",
            """{"nombre":"Juan","apellido":"Garcia"}""", // JSON válido, sin campo de documento
            "https://ejemplo.com/sin-parametros",
        ).forEach { raw ->
            assertNull("caso: '$raw'", ArgentineDniQrParser.parseDniNumber(raw))
        }
    }

    @Test
    fun `json malformado no rompe el parser`() {
        assertNull(ArgentineDniQrParser.parseDniNumber("{dni: 12345678"))
    }
}
