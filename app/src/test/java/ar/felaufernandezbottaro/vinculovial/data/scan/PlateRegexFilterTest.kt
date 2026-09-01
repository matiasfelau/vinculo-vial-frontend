package ar.felaufernandezbottaro.vinculovial.data.scan

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
}
