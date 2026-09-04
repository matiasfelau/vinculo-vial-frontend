package ar.felaufernandezbottaro.vinculovial.domain.signup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialRulesTest {

    private val validForm = SignUpForm(
        dni = "12345678",
        email = "usuario@ejemplo.com",
        password = "Password123!",
        passwordConfirmation = "Password123!",
    )

    @Test
    fun `formulario valido no reporta problemas`() {
        assertTrue(CredentialRules.problems(validForm).isEmpty())
    }

    @Test
    fun `dni acepta 7 y 8 digitos`() {
        assertNull(CredentialRules.problemOf(CredentialField.DNI, validForm.copy(dni = "1234567")))
        assertNull(CredentialRules.problemOf(CredentialField.DNI, validForm.copy(dni = "12345678")))
    }

    @Test
    fun `dni rechaza longitud y caracteres invalidos`() {
        assertEquals(
            CredentialProblem.DNI_FORMAT,
            CredentialRules.problemOf(CredentialField.DNI, validForm.copy(dni = "123456")),
        )
        assertEquals(
            CredentialProblem.DNI_FORMAT,
            CredentialRules.problemOf(CredentialField.DNI, validForm.copy(dni = "12345678A")),
        )
        assertEquals(
            CredentialProblem.REQUIRED,
            CredentialRules.problemOf(CredentialField.DNI, validForm.copy(dni = "")),
        )
    }

    @Test
    fun `email invalido se detecta`() {
        listOf("usuario", "usuario@", "usuario@dominio", "@dominio.com").forEach { email ->
            assertEquals(
                "esperaba EMAIL_FORMAT para '$email'",
                CredentialProblem.EMAIL_FORMAT,
                CredentialRules.problemOf(CredentialField.EMAIL, validForm.copy(email = email)),
            )
        }
    }

    @Test
    fun `password corta o debil se rechaza`() {
        assertEquals(
            CredentialProblem.PASSWORD_TOO_SHORT,
            CredentialRules.problemOf(CredentialField.PASSWORD, validForm.copy(password = "Ab1")),
        )
        assertEquals(
            CredentialProblem.PASSWORD_TOO_WEAK,
            CredentialRules.problemOf(CredentialField.PASSWORD, validForm.copy(password = "solominusculas")),
        )
    }

    @Test
    fun `contrasena que incumple varios requisitos los reporta todos a la vez`() {
        assertEquals(
            setOf(
                PasswordRequirement.MIN_LENGTH,
                PasswordRequirement.UPPERCASE,
                PasswordRequirement.DIGIT,
                PasswordRequirement.SYMBOL,
            ),
            CredentialRules.unmetPasswordRequirements("abc"),
        )
    }

    @Test
    fun `contrasena sin simbolo reporta unicamente ese requisito`() {
        assertEquals(
            setOf(PasswordRequirement.SYMBOL),
            CredentialRules.unmetPasswordRequirements("Password123"),
        )
    }

    @Test
    fun `contrasena valida no reporta requisitos incumplidos`() {
        assertTrue(CredentialRules.unmetPasswordRequirements("Password123!").isEmpty())
    }

    @Test
    fun `confirmacion debe coincidir`() {
        assertEquals(
            CredentialProblem.PASSWORD_MISMATCH,
            CredentialRules.problemOf(
                CredentialField.PASSWORD_CONFIRMATION,
                validForm.copy(passwordConfirmation = "Otra123456"),
            ),
        )
    }

    @Test
    fun `toString del formulario no filtra credenciales`() {
        val rendered = validForm.toString()
        assertTrue(rendered.contains("***"))
        listOf(validForm.dni, validForm.email, validForm.password).forEach {
            assertTrue("toString filtró '$it'", !rendered.contains(it))
        }
    }
}
