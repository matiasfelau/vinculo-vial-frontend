package ar.felaufernandezbottaro.vinculovial.presentation.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInResult
import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInUseCase
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInField
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInForm
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * ViewModel único de la pantalla de login. Sólo habla con `domain`: nunca ve códigos HTTP,
 * excepciones de red ni los tokens en sí (eso lo maneja `SecureTokenStore` dentro del
 * repositorio).
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signIn: SignInUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    /** Barrera sincrónica contra un doble tap en "Iniciar sesión". Ver [submit]. */
    private val submitInFlight = AtomicBoolean(false)

    fun onDniChange(value: String) = updateForm(SignInField.DNI) {
        it.copy(dni = value.filter(Char::isDigit).take(8))
    }

    fun onPasswordChange(value: String) = updateForm(SignInField.PASSWORD) {
        it.copy(password = value)
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return

        val problems = SignInRules.problems(state.form)
        if (problems.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = problems) }
            return
        }

        // Misma barrera sincrónica que SignUpViewModel.submit(): compareAndSet corta un
        // doble tap antes de que la recomposición refleje isSubmitting = true.
        if (!submitInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(status = SignInStatus.Submitting) }

                _uiState.update {
                    when (val result = signIn(state.form)) {
                        is SignInResult.SignedIn -> it.copy(status = SignInStatus.SignedIn)
                        is SignInResult.Failed -> it.copy(status = SignInStatus.Failed(result.error))
                    }
                }
            } finally {
                submitInFlight.set(false)
            }
        }
    }

    private fun updateForm(field: SignInField, transform: (SignInForm) -> SignInForm) {
        _uiState.update { state ->
            state.copy(
                form = transform(state.form),
                fieldErrors = state.fieldErrors - field,
                // Seguir editando tras un rechazo del backend limpia el mensaje genérico:
                // si no, "DNI o contraseña incorrectos" queda pegado en pantalla mientras
                // el usuario corrige.
                status = if (state.status is SignInStatus.Failed) SignInStatus.Idle else state.status,
            )
        }
    }
}
