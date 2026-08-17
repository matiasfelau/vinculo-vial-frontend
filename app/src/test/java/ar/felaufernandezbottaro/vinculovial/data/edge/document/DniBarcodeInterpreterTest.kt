package ar.felaufernandezbottaro.vinculovial.data.edge.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El despacho por formato es lo que decide qué se le dice al usuario cuando el código no
 * sirve. La distinción que importa: un DNI electrónico **no** es "un código cualquiera" —
 * el documento es válido y la app es la que todavía no sabe leerlo, así que el mensaje tiene
 * que ser distinto.
 *
 * No se puede instanciar un `Barcode` de ML Kit en JVM puro (es final y su constructor es
 * interno), así que se prueba el despacho a través de los parsers, que son la parte con
 * lógica real.
 */
class DniBarcodeInterpreterTest {

    private val legacyParser = LegacyDniPdf417Parser()
    private val electronicParser = ElectronicDniQrParser()

    @Test
    fun `un PDF417 valido se parsea como DNI`() {
        val raw = "00307513148@GARCIA@JUAN CARLOS@M@12345678@A@27/01/1990@04/03/2016"

        val dni = legacyParser.parse(raw)

        assertEquals("12345678", dni?.number)
        assertEquals("M", dni?.sex)
        assertEquals("00307513148", dni?.procedureNumber)
    }

    @Test
    fun `el QR del DNI electronico falla ruidosamente en vez de devolver algo inventado`() {
        // Es lo que el intérprete traduce a UnsupportedDocument: el stub tiene que seguir
        // lanzando, no devolver null, para que ese caso no se confunda con "no es un DNI".
        val error = runCatching { electronicParser.parse("cualquier contenido") }.exceptionOrNull()

        assertTrue("el stub debería lanzar NotImplementedError", error is NotImplementedError)
    }

    @Test
    fun `un codigo que no es de un DNI no parsea`() {
        assertEquals(null, legacyParser.parse("https://ejemplo.com/algo"))
    }
}
