package ar.felaufernandezbottaro.vinculovial.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailCodeRulesTest {

    @Test
    fun `sanitize deja solo digitos y corta en 6`() {
        assertEquals("123456", EmailCodeRules.sanitize("12 34-56 78"))
        assertEquals("", EmailCodeRules.sanitize("abc"))
        assertEquals("12", EmailCodeRules.sanitize("1a2"))
    }

    @Test
    fun `un codigo esta completo solo con 6 digitos`() {
        assertTrue(EmailCodeRules.isComplete("123456"))
        assertFalse(EmailCodeRules.isComplete("12345"))
        assertFalse(EmailCodeRules.isComplete("1234567"))
        assertFalse(EmailCodeRules.isComplete("12345a"))
        assertFalse(EmailCodeRules.isComplete(""))
    }

    @Test
    fun `el largo del codigo es el del contrato`() {
        assertEquals(6, EmailCodeRules.CODE_LENGTH)
    }
}
