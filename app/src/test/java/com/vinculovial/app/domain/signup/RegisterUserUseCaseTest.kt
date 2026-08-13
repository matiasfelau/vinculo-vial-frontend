package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.EdgeResult
import com.vinculovial.app.domain.signup.model.SignUpForm
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterUserUseCaseTest {

    private val verification = FakeIdentityVerificationService()
    private val repository = FakeSignUpRepository()
    private val useCase = RegisterUserUseCase(verification, repository)

    private val form = SignUpForm(
        dni = "12345678",
        email = "usuario@ejemplo.com",
        password = "Password123!",
        passwordConfirmation = "Password123!",
    )

    @Test
    fun `registro exitoso manda el dni leido del documento`() = runTest {
        val result = useCase(form)

        assertEquals(SignUpResult.Registered, result)
        assertEquals(1, repository.received.size)
        with(repository.received.single()) {
            assertEquals("12345678", dni)
            assertEquals("usuario@ejemplo.com", email)
            assertEquals("Password123!", password)
        }
    }

    @Test
    fun `formulario invalido no llega ni al edge`() = runTest {
        val result = useCase(form.copy(email = "no-es-un-email"))

        assertEquals(SignUpResult.Failed(SignUpError.InvalidData), result)
        assertEquals(0, verification.verifications)
        assertTrue(repository.received.isEmpty())
    }

    @Test
    fun `documento ilegible corta antes de salir del dispositivo`() = runTest {
        verification.result = EdgeResult(dni = null, isLive = true)

        val result = useCase(form)

        assertEquals(SignUpResult.Failed(SignUpError.DocumentUnreadable), result)
        assertTrue(repository.received.isEmpty())
    }

    @Test
    fun `liveness negativo corta antes de salir del dispositivo`() = runTest {
        verification.result = EdgeResult(dni = "12345678", isLive = false)

        val result = useCase(form)

        assertEquals(SignUpResult.Failed(SignUpError.LivenessFailed), result)
        assertTrue(repository.received.isEmpty())
    }

    @Test
    fun `dni del documento distinto al tipeado corta el registro`() = runTest {
        verification.result = EdgeResult(dni = "87654321", isLive = true)

        val result = useCase(form)

        assertEquals(SignUpResult.Failed(SignUpError.DniMismatch), result)
        assertTrue(repository.received.isEmpty())
    }

    @Test
    fun `el email viaja sin espacios sobrantes`() = runTest {
        useCase(form.copy(email = "  usuario@ejemplo.com  "))

        assertEquals("usuario@ejemplo.com", repository.received.single().email)
    }

    @Test
    fun `el error del backend se propaga tal cual`() = runTest {
        repository.result = SignUpResult.Failed(SignUpError.AlreadyRegistered)

        assertEquals(SignUpResult.Failed(SignUpError.AlreadyRegistered), useCase(form))
    }

    @Test
    fun `siempre se corre el edge antes de publicar`() = runTest {
        useCase(form)

        assertEquals(1, verification.verifications)
    }
}
