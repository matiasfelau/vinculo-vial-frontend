package ar.felaufernandezbottaro.vinculovial.data.scan

import ar.felaufernandezbottaro.vinculovial.domain.scan.model.RecognizedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlateRegexFilterTest {

    private val filter = PlateRegexFilter()

    @Test
    fun `acepta el formato viejo AAA000`() {
        assertEquals("GUQ856", filter.filter(listOf("GUQ856")))
    }

    @Test
    fun `acepta el formato mercosur AA000AA`() {
        assertEquals("AB123CD", filter.filter(listOf("AB123CD")))
    }

    @Test
    fun `normaliza minusculas a mayusculas`() {
        assertEquals("GUQ856", filter.filter(listOf("guq856")))
    }

    @Test
    fun `descarta espacios internos`() {
        assertEquals("GUQ856", filter.filter(listOf("GUQ 856")))
        assertEquals("AB123CD", filter.filter(listOf("AB 123 CD")))
    }

    @Test
    fun `descarta guiones internos`() {
        assertEquals("AB123CD", filter.filter(listOf("AB-123-CD")))
    }

    @Test
    fun `recorta espacios al principio y al final`() {
        assertEquals("GUQ856", filter.filter(listOf("  GUQ856  ")))
    }

    @Test
    fun `rechaza una letra donde el formato espera un digito`() {
        // Confusión típica de OCR (O por 0) en la sección numérica del formato viejo: se
        // descarta en vez de corregir -- ver el KDoc de PlateRegexFilter.
        assertNull(filter.filter(listOf("GUQO56")))
    }

    @Test
    fun `rechaza un digito donde el formato espera una letra`() {
        // Confusión típica de OCR (1 por I) en la sección de letras del formato mercosur.
        assertNull(filter.filter(listOf("AB1231D")))
    }

    @Test
    fun `rechaza texto sin forma de patente`() {
        assertNull(filter.filter(listOf("REPUBLICA ARGENTINA")))
    }

    @Test
    fun `rechaza una cadena vacia`() {
        assertNull(filter.filter(listOf("")))
    }

    @Test
    fun `rechaza candidatos demasiado cortos o demasiado largos`() {
        assertNull(filter.filter(listOf("AB12")))
        assertNull(filter.filter(listOf("AB123CDE")))
    }

    @Test
    fun `sin candidatos no hay match`() {
        assertNull(filter.filter(emptyList()))
    }

    @Test
    fun `prueba cada candidato y devuelve el primero que matchea`() {
        val result = filter.filter(listOf("REPUBLICA ARGENTINA", "MENDOZA", "GUQ856", "OTRO TEXTO"))

        assertEquals("GUQ856", result)
    }

    @Test
    fun `ningun candidato valido entre varios invalidos no matchea`() {
        assertNull(filter.filter(listOf("REPUBLICA ARGENTINA", "MENDOZA", "12345")))
    }

    // --- select: camino del OCR de camara (normaliza y elige por confianza) ---

    private fun text(value: String, confidence: Float) = RecognizedText(value, confidence)

    @Test
    fun `select elige el candidato valido de mayor confianza`() {
        val result = filter.select(listOf(text("AB123CD", 0.5f), text("GUQ856", 0.9f)))

        assertEquals("GUQ856", result)
    }

    @Test
    fun `select ignora un candidato invalido aunque tenga la mayor confianza`() {
        val result = filter.select(listOf(text("REPUBLICA ARGENTINA", 0.99f), text("AB123CD", 0.4f)))

        assertEquals("AB123CD", result)
    }

    @Test
    fun `select normaliza el candidato elegido`() {
        assertEquals("AB123ID", filter.select(listOf(text("AB1231D", 0.8f))))
        assertEquals("GUQ056", filter.select(listOf(text("GUQO56", 0.8f))))
    }

    @Test
    fun `select compara la confianza de los candidatos ya normalizados`() {
        // El corregido (GUQO56 -> GUQ056) tiene más confianza que el que ya venía bien formado.
        val result = filter.select(listOf(text("ABC123", 0.3f), text("GUQO56", 0.9f)))

        assertEquals("GUQ056", result)
    }

    @Test
    fun `select a igual confianza se queda con el primero`() {
        val result = filter.select(listOf(text("GUQ856", 0.7f), text("AB123CD", 0.7f)))

        assertEquals("GUQ856", result)
    }

    @Test
    fun `select no combina lineas ni inventa texto`() {
        // "AB 123" y "CD" juntas serían una patente, pero ninguna lo es por separado.
        assertNull(filter.select(listOf(text("AB 123", 0.9f), text("CD", 0.9f))))
    }

    @Test
    fun `select sin candidatos validos no matchea`() {
        assertNull(filter.select(listOf(text("REPUBLICA ARGENTINA", 0.9f), text("MENDOZA", 0.8f))))
        assertNull(filter.select(emptyList()))
    }

    @Test
    fun `selectMatch conserva la linea cruda y su confianza`() {
        val match = filter.selectMatch(listOf(text("REPUBLICA ARGENTINA", 0.99f), text("ab 1231d", 0.6f)))

        assertEquals("AB123ID", match?.plate)
        assertEquals(text("ab 1231d", 0.6f), match?.source)
    }

    @Test
    fun `filter sigue sin corregir caracteres para lo tipeado a mano`() {
        assertNull(filter.filter(listOf("GUQO56")))
        assertNull(filter.filter(listOf("AB1231D")))
    }
}
