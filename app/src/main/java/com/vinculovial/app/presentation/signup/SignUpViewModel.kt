package com.vinculovial.app.presentation.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vinculovial.app.domain.signup.DocumentScanSession
import com.vinculovial.app.domain.signup.IdentityVerificationService
import com.vinculovial.app.domain.signup.LivenessSession
import com.vinculovial.app.domain.signup.RegisterUserUseCase
import com.vinculovial.app.domain.signup.SignUpError
import com.vinculovial.app.domain.signup.SignUpResult
import com.vinculovial.app.domain.signup.model.CredentialField
import com.vinculovial.app.domain.signup.model.CredentialRules
import com.vinculovial.app.domain.signup.model.SignUpForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    /**
     * Las pantallas de captura las consumen directamente: son sesiones con estado sobre
     * el stream de la cámara, no una operación puntual que el ViewModel pueda envolver.
     */
    val livenessSession: LivenessSession,
    val documentScanSession: DocumentScanSession,
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

    /**
     * El escaneo del frente terminó con un código legible. El ViewModel **no** guarda la
     * imagen: la pasa y suelta la referencia en el acto.
     */
    fun onDocumentScanned(frontImage: ByteArray) = acceptCapture(CaptureTarget.DOCUMENT) {
        identityVerification.acceptDniFront(frontImage)
    }

    /** La selfie, con el veredicto de la prueba de vida ya resuelto en la pantalla. */
    fun onSelfieCaptured(image: ByteArray, livenessPassed: Boolean) = acceptCapture(CaptureTarget.SELFIE) {
        identityVerification.acceptSelfie(image, livenessPassed)
    }

    private fun acceptCapture(target: CaptureTarget, accept: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(capturing = target, status = SignUpStatus.Idle) }

            val accepted = try {
                accept()
                true
            } catch (e: CancellationException) {
                // La pantalla se fue: no es un fallo de captura, hay que dejar propagar.
                throw e
            } catch (e: Exception) {
                false
            }

            _uiState.update { state ->
                if (accepted) {
                    state.copy(
                        capturing = null,
                        completedCaptures = state.completedCaptures + target,
                    )
                } else {
                    state.copy(
                        capturing = null,
                        status = SignUpStatus.Failed(SignUpError.DocumentUnreadable),
                    )
                }
            }
        }
    }

    /** Vuelve a empezar la captura: las imágenes pendientes se descartan. */
    fun onCaptureRestarted() {
        identityVerification.discardCaptures()
        _uiState.update {
            it.copy(
                completedCaptures = emptySet(),
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
                        completedCaptures = emptySet(),
                    )

                    is SignUpResult.Failed -> it.copy(
                        status = SignUpStatus.Failed(result.error),
                        // Las imágenes ya no existen: cualquier reintento vuelve a capturar.
                        completedCaptures = emptySet(),
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
