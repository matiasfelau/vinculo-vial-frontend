package ar.felaufernandezbottaro.vinculovial.presentation.signin

import ar.felaufernandezbottaro.vinculovial.domain.signin.FakeSignInRepository
import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInError
import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInResult
import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInUseCase
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInField
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInFieldProblem
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignInViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSignInRepository()

    private fun viewModel() = SignInViewModel(SignInUseCase(repository))

    private fun SignInViewModel.completarFormulario() {
        onDniChange("12345678")
        onPasswordChange("Password123!")
    }

    @Test
    fun `el dni solo acepta digitos y hasta 8`() {
        val viewModel = viewModel()

        viewModel.onDniChange("12a34-5678999")

        assertEquals("12345678", viewModel.uiState.value.form.dni)
    }

    @Test
    fun `enviar un formulario vacio marca los campos sin llegar al repositorio`() = runTest {
        val viewModel = viewModel()

        viewModel.submit()

        assertEquals(
            SignInFieldProblem.REQUIRED,
            viewModel.uiState.value.fieldErrors[SignInField.DNI],
        )
        assertEquals(
            SignInFieldProblem.REQUIRED,
            viewModel.uiState.value.fieldErrors[SignInField.PASSWORD],
        )
        assertTrue(repository.received.isEmpty())
    }

    @Test
    fun `editar un campo limpia su error`() = runTest {
        val viewModel = viewModel()
        viewModel.submit()

        viewModel.onDniChange("12345678")

        assertFalse(viewModel.uiState.value.fieldErrors.containsKey(SignInField.DNI))
    }

    @Test
    fun `login exitoso deja el estado en SignedIn`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()

        viewModel.submit()

        assertEquals(SignInStatus.SignedIn, viewModel.uiState.value.status)
        assertEquals(1, repository.received.size)
    }

    @Test
    fun `un rechazo del backend queda como error de dominio`() = runTest {
        repository.result = SignInResult.Failed(SignInError.InvalidCredentials)
        val viewModel = viewModel()
        viewModel.completarFormulario()

        viewModel.submit()

        assertEquals(SignInError.InvalidCredentials, viewModel.uiState.value.error)
    }

    @Test
    fun `seguir editando tras un rechazo limpia el mensaje generico`() = runTest {
        repository.result = SignInResult.Failed(SignInError.InvalidCredentials)
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.submit()

        viewModel.onPasswordChange("OtraPassword123!")

        assertEquals(SignInStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `400 401 y 403 del backend llegan tal cual al estado, sin marcar campos`() = runTest {
        // A diferencia de la validación local (fieldErrors por campo), un rechazo del
        // backend siempre es un mensaje genérico único — nunca señala cuál de los dos
        // campos falló, ni siquiera para el 400.
        listOf(
            SignInError.InvalidData,
            SignInError.InvalidCredentials,
            SignInError.AttestationRejected,
        ).forEach { error ->
            repository.result = SignInResult.Failed(error)
            val viewModel = viewModel()
            viewModel.completarFormulario()

            viewModel.submit()

            assertEquals("mapeo incorrecto para $error", error, viewModel.uiState.value.error)
            assertTrue(
                "$error no debería marcar campos individuales",
                viewModel.uiState.value.fieldErrors.isEmpty(),
            )
        }
    }
}
