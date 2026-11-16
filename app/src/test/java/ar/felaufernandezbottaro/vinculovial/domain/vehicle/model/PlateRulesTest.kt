package ar.felaufernandezbottaro.vinculovial.domain.vehicle.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateRulesTest {

    @Test
    fun `normalize pasa a mayusculas y saca espacios, guiones y simbolos`() {
        assertEquals("AB123CD", PlateRules.normalize("ab 123-cd"))
        assertEquals("ABC123", PlateRules.normalize(" abc.123 "))
        assertEquals("ABC123", PlateRules.normalize("a_b/c#1@2!3"))
    }

    @Test
    fun `normalize corta en 7 caracteres`() {
        assertEquals("AB123CD", PlateRules.normalize("ab123cdxyz999"))
        assertEquals(PlateRules.MAX_LENGTH, PlateRules.normalize("abcdefghijkl").length)
    }

    @Test
    fun `normalize deja solo A-Z y 0-9, sin letras con tilde ni la enie`() {
        assertEquals("B123C", PlateRules.normalize("ÁB1ñ23C"))
    }

    @Test
    fun `acepta AAA999 y AA999AA`() {
        assertTrue(PlateRules.isValid("ABC123"))
        assertTrue(PlateRules.isValid("AB123CD"))
    }

    @Test
    fun `rechaza cualquier otra forma`() {
        listOf(
            "", "AB123", "ABC12", "ABCD123", "AB1234", "AB12CD", "A1B23CD", "1234567", "ABCDEFG",
            "ABC1234", "AB123C", "AB123CDE", "abc123", "AB 123CD", "AB-123-CD",
        ).forEach { assertFalse("'$it' no debería ser válida", PlateRules.isValid(it)) }
    }

    @Test
    fun `no corrige O por cero ni I por uno`() {
        assertFalse(PlateRules.isValid("AB1O3CD"))
        assertFalse(PlateRules.isValid("AB1I3CD"))
    }

    @Test
    fun `clean no corta, para que una patente larga no se valide por su prefijo`() {
        assertEquals("AB123CDE", PlateRules.clean("ab-123 cde"))
        assertFalse(PlateRules.isValid(PlateRules.clean("AB123CDE")))
    }
}
