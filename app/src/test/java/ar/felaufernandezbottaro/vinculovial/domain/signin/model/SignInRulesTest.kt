package ar.felaufernandezbottaro.vinculovial.domain.signin.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInRulesTest {

    private val validForm = SignInForm(dni = "12345678", password = "Password123!")

    @Test
    fun `formulario valido no reporta problemas`() {
        assertTrue(SignInRules.problems(validForm).isEmpty())
    }

    @Test
    fun `dni acepta 7 y 8 digitos`() {
        assertNull(SignInRules.problemOf(SignInField.DNI, validForm.copy(dni = "1234567")))
        assertNull(SignInRules.problemOf(SignInField.DNI, validForm.copy(dni = "12345678")))
    }

    @Test
    fun `dni rechaza letras y longitud invalida`() {
        assertEquals(
            SignInFieldProblem.DNI_FORMAT,
            SignInRules.problemOf(SignInField.DNI, validForm.copy(dni = "12345678A")),
        )
        assertEquals(
            SignInFieldProblem.DNI_FORMAT,
            SignInRules.problemOf(SignInField.DNI, validForm.copy(dni = "123456")),
        )
        assertEquals(
            SignInFieldProblem.REQUIRED,
            SignInRules.problemOf(SignInField.DNI, validForm.copy(dni = "")),
        )
    }

    @Test
    fun `contrasena vacia se rechaza sin evaluar fortaleza`() {
        // A diferencia de CredentialRules (US-01), acá no se está creando una contraseña:
        // cualquier valor no vacío es candidato, la fortaleza real la evalúa el backend
        // contra la que ya está guardada.
        assertEquals(
            SignInFieldProblem.REQUIRED,
            SignInRules.problemOf(SignInField.PASSWORD, validForm.copy(password = "")),
        )
        assertNull(SignInRules.problemOf(SignInField.PASSWORD, validForm.copy(password = "1")))
    }

    @Test
    fun `toString del formulario no filtra credenciales`() {
        val rendered = validForm.toString()
        assertTrue(rendered.contains("***"))
        listOf(validForm.dni, validForm.password).forEach {
            assertTrue("toString filtró '$it'", !rendered.contains(it))
        }
    }
}
