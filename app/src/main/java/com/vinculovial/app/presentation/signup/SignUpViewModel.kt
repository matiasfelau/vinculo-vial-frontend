package com.vinculovial.app.presentation.signup

import androidx.lifecycle.ViewModel
import com.vinculovial.app.domain.signup.model.CredentialField
import com.vinculovial.app.domain.signup.model.CredentialRules
import com.vinculovial.app.domain.signup.model.SignUpForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel único del flujo de registro; se scopea al nav graph `signup` para que
 * el formulario sobreviva a la navegación entre formulario, consentimiento y captura.
 */
@HiltViewModel
class SignUpViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun onDniChange(value: String) = updateForm(CredentialField.DNI) {
        it.copy(dni = value.filter(Char::isDigit).take(8))
    }

    fun onEmailChange(value: String) = updateForm(CredentialField.EMAIL) {
        it.copy(email = value.trim())
    }

    fun onPasswordChange(value: String) = updateForm(CredentialField.PASSWORD) {
        it.copy(password = value)
    }

    fun onPasswordConfirmationChange(value: String) = updateForm(CredentialField.PASSWORD_CONFIRMATION) {
        it.copy(passwordConfirmation = value)
    }

    /** Valida todo el formulario; `true` habilita avanzar al consentimiento. */
    fun validateForm(): Boolean {
        val problems = CredentialRules.problems(_uiState.value.form)
        _uiState.update { it.copy(fieldErrors = problems) }
        return problems.isEmpty()
    }

    fun onDniImageCaptured() = _uiState.update { it.copy(dniImageCaptured = true) }

    fun onSelfieCaptured() = _uiState.update { it.copy(selfieCaptured = true) }

    fun onCaptureRestarted() = _uiState.update {
        it.copy(dniImageCaptured = false, selfieCaptured = false)
    }

    /** Al cambiar un campo se limpia su error para no pelear con el usuario mientras tipea. */
    private fun updateForm(field: CredentialField, transform: (SignUpForm) -> SignUpForm) {
        _uiState.update { state ->
            state.copy(
                form = transform(state.form),
                fieldErrors = state.fieldErrors - field,
            )
        }
    }
}
