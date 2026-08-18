package ar.felaufernandezbottaro.vinculovial.data.edge.document

import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Stub deliberado: RENAPER no publicó el formato del QR del DNI electrónico, así que acá
 * no hay heurística que probar, sólo que el fallo sea explícito y no un `null` silencioso
 * que se confunda con "no había código".
 */
class ElectronicDniQrParserTest {

    private val parser = ElectronicDniQrParser()

    @Test
    fun `siempre falla explicitamente, nunca inventa un numero`() {
        val error = assertThrows(NotImplementedError::class.java) {
            parser.parse("""{"dni":"12345678"}""")
        }

        assertThrows(NotImplementedError::class.java) { parser.parse("") }
        assertThrows(NotImplementedError::class.java) { parser.parse("cualquier cosa") }

        assert(error.message?.contains("Renaper") == true) {
            "el mensaje debería explicar por qué, no sólo que falló"
        }
    }
}
