package com.vinculovial.app.presentation.signup

import com.vinculovial.app.domain.signup.FakeIdentityVerificationService
import com.vinculovial.app.domain.signup.FakeSignUpRepository
import com.vinculovial.app.domain.signup.RegisterUserUseCase
import com.vinculovial.app.domain.signup.SignUpError
import com.vinculovial.app.domain.signup.SignUpResult
import com.vinculovial.app.domain.signup.model.CredentialField
import com.vinculovial.app.domain.signup.model.CredentialProblem
import com.vinculovial.app.domain.signup.model.EdgeResult
import com.vinculovial.app.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignUpViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val verification = FakeIdentityVerificationService()
    private val repository = FakeSignUpRepository()

    private fun viewModel() = SignUpViewModel(
        registerUser = RegisterUserUseCase(verification, repository),
        identityVerification = verification,
    )

    private fun SignUpViewModel.completarFormulario() {
        onDniChange("12345678")
        onEmailChange("usuario@ejemplo.com")
        onPasswordChange("Password123!")
        onPasswordConfirmationChange("Password123!")
    }

    private suspend fun SignUpViewModel.capturarTodo() {
        onCaptureDni()
        onCaptureSelfie()
    }

    @Test
    fun `el dni solo acepta digitos y hasta 8`() {
        val viewModel = viewModel()

        viewModel.onDniChange("12a34-5678999")

        assertEquals("12345678", viewModel.uiState.value.form.dni)
    }

    @Test
    fun `validar un formulario incompleto marca los campos`() {
        val viewModel = viewModel()

        val valido = viewModel.validateForm()

        assertFalse(valido)
        assertEquals(
            CredentialProblem.REQUIRED,
            viewModel.uiState.value.fieldErrors[CredentialField.EMAIL],
        )
    }

    @Test
    fun `editar un campo limpia su error`() {
        val viewModel = viewModel()
        viewModel.validateForm()

        viewModel.onEmailChange("usuario@ejemplo.com")

        assertFalse(viewModel.uiState.value.fieldErrors.containsKey(CredentialField.EMAIL))
    }

    @Test
    fun `capturar el dni le pasa al edge el numero tipeado`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()

        viewModel.onCaptureDni()

        assertEquals(listOf("12345678"), verification.capturedDniClaims)
        assertTrue(viewModel.uiState.value.dniImageCaptured)
        assertEquals(null, viewModel.uiState.value.capturing)
    }

    @Test
    fun `no se puede enviar sin las dos capturas`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.onCaptureDni()

        viewModel.submit()

        assertTrue(repository.received.isEmpty())
        assertEquals(SignUpStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `registro exitoso deja el estado en Registered y sin capturas`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.submit()

        val state = viewModel.uiState.value
        assertEquals(SignUpStatus.Registered, state.status)
        assertFalse(state.dniImageCaptured)
        assertFalse(state.selfieCaptured)
    }

    @Test
    fun `un rechazo del backend queda como error de dominio`() = runTest {
        repository.result = SignUpResult.Failed(SignUpError.AlreadyRegistered)
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.submit()

        assertEquals(SignUpError.AlreadyRegistered, viewModel.uiState.value.error)
    }

    @Test
    fun `un liveness negativo obliga a capturar de nuevo`() = runTest {
        verification.result = EdgeResult(dni = "12345678", isLive = false)
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.submit()

        val state = viewModel.uiState.value
        assertEquals(SignUpError.LivenessFailed, state.error)
        assertFalse(state.dniImageCaptured)
        assertFalse(state.selfieCaptured)
    }

    @Test
    fun `descartar el error deja la pantalla lista para reintentar`() = runTest {
        repository.result = SignUpResult.Failed(SignUpError.Network)
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()
        viewModel.submit()

        viewModel.onErrorDismissed()

        assertEquals(SignUpStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `volver atras descarta las imagenes pendientes`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.onCaptureRestarted()

        assertEquals(1, verification.discards)
        assertFalse(viewModel.uiState.value.dniImageCaptured)
        assertFalse(viewModel.uiState.value.selfieCaptured)
    }

    @Test
    fun `si la captura falla se informa sin dejar la pantalla trabada`() = runTest {
        verification.failOnCapture = true
        val viewModel = viewModel()
        viewModel.completarFormulario()

        viewModel.onCaptureDni()

        val state = viewModel.uiState.value
        assertEquals(SignUpError.DocumentUnreadable, state.error)
        assertFalse(state.dniImageCaptured)
        assertEquals(null, state.capturing)
    }
}
