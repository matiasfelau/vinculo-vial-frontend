package ar.felaufernandezbottaro.vinculovial.domain.recovery

import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure
import ar.felaufernandezbottaro.vinculovial.domain.signup.FakeIdentityVerificationService
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentIdentity
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.EdgeResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletePasswordResetUseCaseTest {

    private val verification = FakeIdentityVerificationService()
    private val repository = FakePasswordResetRepository()
    private val useCase = CompletePasswordResetUseCase(verification, repository)

    private val token = "reset-token"
    private val password = "Nueva-Clave-2026!"

    private fun verified(number: String) = EdgeResult.Verified(
        document = DocumentIdentity(number = number, sex = "F", procedureNumber = "00307513148"),
        selfieBase64 = "c2VsZmll",
    )

    @Test
    fun `manda los datos leidos del documento y no lo que se tipeo`() = runTest {
        verification.result = verified(number = "01234567")

        val outcome = useCase(token, enteredDni = "1234567", newPassword = password)

        assertEquals(
            CompletePasswordResetUseCase.Outcome.Finished(PasswordResetResult.Done),
            outcome,
        )
        with(repository.completions.single()) {
            assertEquals(token, resetToken)
            assertEquals("01234567", dni)
            assertEquals("00307513148", procedureNumber)
            assertEquals("F", sex)
            assertEquals("c2VsZmll", selfieBase64)
            assertEquals(password, newPassword)
        }
    }

    @Test
    fun `un dni escaneado distinto al ingresado no llega al backend`() = runTest {
        verification.result = verified(number = "87654321")

        val outcome = useCase(token, enteredDni = "12345678", newPassword = password)

        assertEquals(CompletePasswordResetUseCase.Outcome.DniMismatch, outcome)
        assertTrue(repository.completions.isEmpty())
    }

    @Test
    fun `los ceros a la izquierda no cuentan como documentos distintos`() {
        assertTrue(CompletePasswordResetUseCase.documentMatches("01234567", "1234567"))
        assertTrue(CompletePasswordResetUseCase.documentMatches(" 1234567 ", "01234567"))
        assertFalse(CompletePasswordResetUseCase.documentMatches("12345678", "12345679"))
    }

    @Test
    fun `cada rechazo del edge tiene su resultado y nunca llega al backend`() = runTest {
        val esperados = mapOf(
            EdgeResult.Reason.LIVENESS_FAILED to CompletePasswordResetUseCase.Outcome.LivenessFailed,
            EdgeResult.Reason.INCOMPLETE_CAPTURE to CompletePasswordResetUseCase.Outcome.CaptureUnusable,
            EdgeResult.Reason.SELFIE_UNUSABLE to CompletePasswordResetUseCase.Outcome.CaptureUnusable,
        )

        esperados.forEach { (reason, expected) ->
            verification.result = EdgeResult.Rejected(reason)

            assertEquals("mapeo incorrecto para $reason", expected, useCase(token, "12345678", password))
        }
        assertTrue(repository.completions.isEmpty())
    }

    @Test
    fun `una contrasena que no cumple las reglas del alta no llega ni al edge`() = runTest {
        val outcome = useCase(token, "12345678", newPassword = "corta")

        assertEquals(CompletePasswordResetUseCase.Outcome.InvalidPassword, outcome)
        assertEquals(0, verification.verifications)
        assertTrue(repository.completions.isEmpty())
    }

    @Test
    fun `el resultado del backend se propaga tal cual`() = runTest {
        repository.completeResult = PasswordResetResult.IdentityNotVerified
        assertEquals(
            CompletePasswordResetUseCase.Outcome.Finished(PasswordResetResult.IdentityNotVerified),
            useCase(token, "12345678", password),
        )

        repository.completeResult = PasswordResetResult.Failed(RequestFailure.NETWORK)
        assertEquals(
            CompletePasswordResetUseCase.Outcome.Finished(PasswordResetResult.Failed(RequestFailure.NETWORK)),
            useCase(token, "12345678", password),
        )
    }

    @Test
    fun `siempre se corre el edge antes de publicar`() = runTest {
        useCase(token, "12345678", password)

        assertEquals(1, verification.verifications)
    }
}
