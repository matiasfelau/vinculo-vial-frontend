package ar.felaufernandezbottaro.vinculovial.data.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlateNormalizerTest {

    // --- patentes ya bien formadas: no se tocan ---

    @Test
    fun `deja intacta una patente valida del formato viejo`() {
        assertEquals("GUQ856", PlateNormalizer.normalize("GUQ856"))
    }

    @Test
    fun `deja intacta una patente valida del formato mercosur`() {
        assertEquals("AB123CD", PlateNormalizer.normalize("AB123CD"))
    }

    @Test
    fun `limpia mayusculas espacios y guiones`() {
        assertEquals("AB123CD", PlateNormalizer.normalize("  ab 123-cd "))
        assertEquals("GUQ856", PlateNormalizer.normalize("guq 856"))
    }

    // --- posiciones de letra: digito -> letra ---

    @Test
    fun `en posicion de letra convierte 0 a O`() {
        assertEquals("OAA111", PlateNormalizer.normalize("0AA111"))
    }

    @Test
    fun `en posicion de letra convierte 1 a I`() {
        assertEquals("AIA111", PlateNormalizer.normalize("A1A111"))
    }

    @Test
    fun `en posicion de letra convierte 8 a B`() {
        assertEquals("AAB111", PlateNormalizer.normalize("AA8111"))
    }

    @Test
    fun `en posicion de letra convierte 5 a S`() {
        assertEquals("SAA111", PlateNormalizer.normalize("5AA111"))
    }

    @Test
    fun `en posicion de letra convierte 2 a Z`() {
        assertEquals("AZA111", PlateNormalizer.normalize("A2A111"))
    }

    @Test
    fun `en posicion de letra convierte 6 a G`() {
        assertEquals("AAG111", PlateNormalizer.normalize("AA6111"))
    }

    // --- posiciones de numero: letra -> digito ---

    @Test
    fun `en posicion de numero convierte O a 0`() {
        assertEquals("AAA011", PlateNormalizer.normalize("AAAO11"))
    }

    @Test
    fun `en posicion de numero convierte I a 1`() {
        assertEquals("AAA111", PlateNormalizer.normalize("AAAI11"))
    }

    @Test
    fun `en posicion de numero convierte B a 8`() {
        assertEquals("AAA811", PlateNormalizer.normalize("AAAB11"))
    }

    @Test
    fun `en posicion de numero convierte S a 5`() {
        assertEquals("AAA511", PlateNormalizer.normalize("AAAS11"))
    }

    @Test
    fun `en posicion de numero convierte Z a 2`() {
        assertEquals("AAA211", PlateNormalizer.normalize("AAAZ11"))
    }

    @Test
    fun `en posicion de numero convierte G a 6`() {
        assertEquals("AAA611", PlateNormalizer.normalize("AAAG11"))
    }

    // --- formato mercosur ---

    @Test
    fun `mercosur corrige las dos secciones de letras y la de numeros`() {
        // 0->O y 1->I (letras iniciales); O->0 (numero); 8->B (letra final)
        assertEquals("OI105CB", PlateNormalizer.normalize("011O5C8"))
    }

    @Test
    fun `mercosur con un digito en la seccion final de letras`() {
        assertEquals("AB123ID", PlateNormalizer.normalize("AB1231D"))
    }

    @Test
    fun `mercosur con una letra en la seccion de numeros`() {
        assertEquals("AB125CD", PlateNormalizer.normalize("AB12SCD"))
    }

    @Test
    fun `corrige y limpia a la vez`() {
        assertEquals("AB103CD", PlateNormalizer.normalize("ab 1o3 cd"))
    }

    @Test
    fun `viejo con O en la seccion numerica`() {
        assertEquals("GUQ056", PlateNormalizer.normalize("GUQO56"))
    }

    // --- rechazos: no se inventa nada ---

    @Test
    fun `rechaza un digito sin conversion en posicion de letra`() {
        listOf("3AA111", "A4A111", "AA7111", "9AA111").forEach {
            assertNull(it, PlateNormalizer.normalize(it))
        }
    }

    @Test
    fun `rechaza una letra sin conversion en posicion de numero`() {
        assertNull(PlateNormalizer.normalize("AAAX11"))
        assertNull(PlateNormalizer.normalize("AAA1A1"))
        assertNull(PlateNormalizer.normalize("AB1C3DE"))
    }

    @Test
    fun `rechaza simbolos y letras fuera de A-Z`() {
        assertNull(PlateNormalizer.normalize("AB123C!"))
        assertNull(PlateNormalizer.normalize("ÁBC123"))
        assertNull(PlateNormalizer.normalize("ABC12Ñ"))
    }

    @Test
    fun `el largo fija el formato y no se reinterpreta`() {
        // 6 caracteres: es formato viejo (LLLNNN); la C en posicion de numero no tiene conversion.
        assertNull(PlateNormalizer.normalize("AB123C"))
        // 7 caracteres: es mercosur (LLNNNLL); ABC1234 tiene una C donde van numeros.
        assertNull(PlateNormalizer.normalize("ABC1234"))
    }

    @Test
    fun `rechaza largos distintos de 6 y 7`() {
        assertNull(PlateNormalizer.normalize(""))
        assertNull(PlateNormalizer.normalize("AB12"))
        assertNull(PlateNormalizer.normalize("AB123CDE"))
        assertNull(PlateNormalizer.normalize("REPUBLICA ARGENTINA"))
    }

    @Test
    fun `es idempotente`() {
        listOf("GUQO56", "AB1231D", "ab 12s cd", "0AA111").forEach {
            val once = PlateNormalizer.normalize(it)
            assertEquals(once, once?.let(PlateNormalizer::normalize))
        }
    }
}
