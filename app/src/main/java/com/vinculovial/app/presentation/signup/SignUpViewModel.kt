package com.vinculovial.app.presentation.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vinculovial.app.domain.signup.IdentityVerificationService
import com.vinculovial.app.domain.signup.RegisterUserUseCase
import com.vinculovial.app.domain.signup.SignUpError
import com.vinculovial.app.domain.signup.SignUpResult
import com.vinculovial.app.domain.signup.model.CredentialField
import com.vinculovial.app.domain.signup.model.CredentialRules
import com.vinculovial.app.domain.signup.model.SignUpForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel único del flujo de registro; se scopea al nav graph `signup` para que
 * el formulario sobreviva a la navegación entre formulario, consentimiento y captura.
 *
 * Sólo habla con `domain`: el use case y el puerto de verificación. Nunca ve bytes de
 * imagen, códigos HTTP ni excepciones de red.
 */
@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val registerUser: RegisterUserUseCase,
    private val identityVerification: IdentityVerificationService,
) : ViewModel() {

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

    fun onCaptureDni() = capture(CaptureTarget.DNI)

    fun onCaptureSelfie() = capture(CaptureTarget.SELFIE)

    /** Vuelve a empezar la captura: las imágenes pendientes se descartan. */
    fun onCaptureRestarted() {
        identityVerification.discardCaptures()
        _uiState.update {
            it.copy(
                dniImageCaptured = false,
                selfieCaptured = false,
                capturing = null,
                status = SignUpStatus.Idle,
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmitCapture) return

        viewModelScope.launch {
            _uiState.update { it.copy(status = SignUpStatus.Submitting) }

            val result = registerUser(state.form)

            _uiState.update {
                when (result) {
                    is SignUpResult.Registered -> it.copy(
                        status = SignUpStatus.Registered,
                        // El Edge ya descartó las imágenes al verificar.
                        dniImageCaptured = false,
                        selfieCaptured = false,
                    )

                    is SignUpResult.Failed -> it.copy(
                        status = SignUpStatus.Failed(result.error),
                        // Los errores de verificación obligan a capturar de nuevo.
                        dniImageCaptured = false,
                        selfieCaptured = false,
                    )
                }
            }
        }
    }

    /** Cierra el error y deja la pantalla lista para reintentar. */
    fun onErrorDismissed() {
        if (_uiState.value.status is SignUpStatus.Failed) {
            _uiState.update { it.copy(status = SignUpStatus.Idle) }
        }
    }

    private fun capture(target: CaptureTarget) {
        val state = _uiState.value
        if (state.isBusy) return

        viewModelScope.launch {
            _uiState.update { it.copy(capturing = target, status = SignUpStatus.Idle) }

            val captured = runCatching {
                when (target) {
                    CaptureTarget.DNI -> identityVerification.captureDniFront(state.form.dni)
                    CaptureTarget.SELFIE -> identityVerification.captureSelfie()
                }
            }.isSuccess

            _uiState.update {
                when {
                    !captured -> it.copy(
                        capturing = null,
                        status = SignUpStatus.Failed(SignUpError.DocumentUnreadable),
                    )

                    target == CaptureTarget.DNI -> it.copy(capturing = null, dniImageCaptured = true)
                    else -> it.copy(capturing = null, selfieCaptured = true)
                }
            }
        }
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

    /** Si la pantalla muere con capturas pendientes, las imágenes no quedan vivas. */
    override fun onCleared() {
        identityVerification.discardCaptures()
        super.onCleared()
    }
}
