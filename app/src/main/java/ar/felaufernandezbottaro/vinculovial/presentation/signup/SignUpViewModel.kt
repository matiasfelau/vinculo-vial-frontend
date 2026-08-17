package ar.felaufernandezbottaro.vinculovial.presentation.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.domain.signup.DocumentScanSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.IdentityVerificationService
import ar.felaufernandezbottaro.vinculovial.domain.signup.LivenessSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.RegisterUserUseCase
import ar.felaufernandezbottaro.vinculovial.domain.signup.SignUpError
import ar.felaufernandezbottaro.vinculovial.domain.signup.SignUpResult
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialField
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialRules
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentIdentity
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.SignUpForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
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
     * Las dos pantallas de cámara consumen su sesión directamente: son sesiones con estado
     * sobre el stream, no operaciones puntuales que el ViewModel pueda envolver.
     */
    val livenessSession: LivenessSession,
    /**
     * Escaneo del DNI en vivo: decodifica el código directo del stream y entrega los campos
     * ya parseados. No produce ninguna imagen del documento.
     */
    val documentScanSession: DocumentScanSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    /** Barrera sincrónica contra un doble tap en "Verificar y registrarme". Ver [submit]. */
    private val submitInFlight = AtomicBoolean(false)

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
     * El escaneo en vivo leyó el código. Lo que llega son tres campos de texto, no una
     * imagen: del documento nunca se produce una.
     */
    fun onDocumentScanned(document: DocumentIdentity) = acceptCapture(CaptureTarget.DOCUMENT) {
        identityVerification.acceptDocument(document)
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

        // canSubmitCapture ya exige !isBusy, pero eso sólo se refleja en la UI después de
        // una recomposición: un doble tap en la misma fracción de segundo puede llamar a
        // submit() dos veces antes de que _uiState.update de la primera llegue a
        // aplicarse. Este flag es la barrera real, sincrónica, contra esa carrera.
        if (!submitInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
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
            } finally {
                submitInFlight.set(false)
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
