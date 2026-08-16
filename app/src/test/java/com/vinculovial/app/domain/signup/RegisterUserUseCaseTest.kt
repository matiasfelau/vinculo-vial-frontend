package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.DocumentIdentity
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
    fun `registro exitoso manda los datos del documento, las credenciales y la selfie`() = runTest {
        val result = useCase(form)

        assertEquals(SignUpResult.Registered, result)
        with(repository.received.single()) {
            assertEquals("12345678", dni)
            assertEquals("00307513148", procedureNumber)
            assertEquals("M", sex)
            assertEquals("usuario@ejemplo.com", email)
            assertEquals("Password123!", password)
            assertEquals("c2VsZmll", selfieBase64)
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
    fun `cada rechazo del edge tiene su error de dominio`() = runTest {
        val esperados = mapOf(
            EdgeResult.Reason.BARCODE_NOT_FOUND to SignUpError.DocumentUnreadable,
            EdgeResult.Reason.BARCODE_NOT_A_DNI to SignUpError.DocumentNotRecognized,
            EdgeResult.Reason.MISSING_PROCEDURE_NUMBER to SignUpError.DocumentUnreadable,
            EdgeResult.Reason.LIVENESS_FAILED to SignUpError.LivenessFailed,
            EdgeResult.Reason.INCOMPLETE_CAPTURE to SignUpError.DocumentUnreadable,
        )

        esperados.forEach { (reason, expectedError) ->
            verification.result = EdgeResult.Rejected(reason)
            repository.received.clear()

            assertEquals(
                "mapeo incorrecto para $reason",
                SignUpResult.Failed(expectedError),
                useCase(form),
            )
            assertTrue("$reason llegó al backend", repository.received.isEmpty())
        }
    }

    @Test
    fun `un rechazo del edge nunca sale del dispositivo`() = runTest {
        verification.result = EdgeResult.Rejected(EdgeResult.Reason.LIVENESS_FAILED)

        useCase(form)

        assertTrue(repository.received.isEmpty())
    }

    @Test
    fun `el dni del documento distinto al tipeado corta el registro`() = runTest {
        verification.result = verified(number = "87654321")

        val result = useCase(form)

        assertEquals(SignUpResult.Failed(SignUpError.DniMismatch), result)
        assertTrue(repository.received.isEmpty())
    }

    @Test
    fun `los ceros a la izquierda no cuentan como documentos distintos`() = runTest {
        // El PDF417 suele venir con relleno; el usuario casi nunca lo tipea.
        verification.result = verified(number = "01234567")

        val result = useCase(form.copy(dni = "1234567"))

        assertEquals(SignUpResult.Registered, result)
        assertEquals("01234567", repository.received.single().dni)
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

    private fun verified(number: String) = EdgeResult.Verified(
        document = DocumentIdentity(number = number, sex = "M", procedureNumber = "00307513148"),
        selfieBase64 = "c2VsZmll",
    )
}
