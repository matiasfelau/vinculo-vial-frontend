package ar.felaufernandezbottaro.vinculovial.presentation.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.di.EmailCodeResendSeconds
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeConfirmResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeRequestResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.EmailCodeRules
import ar.felaufernandezbottaro.vinculovial.domain.recovery.CompletePasswordResetUseCase
import ar.felaufernandezbottaro.vinculovial.domain.recovery.PasswordResetRepository
import ar.felaufernandezbottaro.vinculovial.domain.recovery.PasswordResetResult
import ar.felaufernandezbottaro.vinculovial.domain.recovery.RecoverySessionCleaner
import ar.felaufernandezbottaro.vinculovial.domain.session.LoginNotice
import ar.felaufernandezbottaro.vinculovial.domain.session.LoginNoticeStore
import ar.felaufernandezbottaro.vinculovial.domain.signup.DocumentScanSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.IdentityVerificationService
import ar.felaufernandezbottaro.vinculovial.domain.signup.LivenessSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialField
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialRules
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentIdentity
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.SignUpForm
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeFlowError
import ar.felaufernandezbottaro.vinculovial.presentation.common.ResendCountdown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Lo que el `NavHost` tiene que hacer ante un resultado del grafo `recovery`. */
sealed interface RecoveryEvent {

    /** El pedido del código se aceptó: se pasa a la pantalla del código. */
    data object CodeRequested : RecoveryEvent

    /** El código era correcto: se pasa a elegir la contraseña nueva. */
    data object CodeConfirmed : RecoveryEvent

    /** La contraseña cambió y la sesión local ya se borró: se va a Login con el aviso. */
    data object Completed : RecoveryEvent

    /** El token ya no sirve: se vuelve al primer paso para pedir otro código. */
    data object RestartRequested : RecoveryEvent
}

/**
 * ViewModel único del grafo `recovery`: DNI, código, contraseña nueva, captura y envío (US-13).
 *
 * El `reset_token` queda sólo en memoria, acá; ni él, ni el código, ni la contraseña, ni el DNI se
 * loguean. Del documento y la selfie se ocupa `IdentityVerificationService`, que los descarta al
 * enviar.
 */
@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val repository: PasswordResetRepository,
    private val completePasswordReset: CompletePasswordResetUseCase,
    private val identityVerification: IdentityVerificationService,
    private val sessionCleaner: RecoverySessionCleaner,
    private val loginNotice: LoginNoticeStore,
    @EmailCodeResendSeconds resendSeconds: Int,
    /** Las pantallas de cámara consumen su sesión directamente, igual que en el alta. */
    val livenessSession: LivenessSession,
    val documentScanSession: DocumentScanSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    private val _events = Channel<RecoveryEvent>(Channel.BUFFERED)
    val events: Flow<RecoveryEvent> = _events.receiveAsFlow()

    private val countdown = ResendCountdown(viewModelScope, resendSeconds)

    /** Barrera sincrónica contra un doble tap en los botones que llaman a la red. */
    private val inFlight = AtomicBoolean(false)

    /** `reset_token`: sólo en memoria, hasta que se usa, se rechaza o se va la pantalla. */
    private var resetToken: String? = null

    init {
        // Un resto de otra captura (el alta, por ejemplo) no puede colarse en la recuperación.
        identityVerification.discardCaptures()
        viewModelScope.launch {
            countdown.remainingSeconds.collect { seconds -> _uiState.update { it.copy(resendSeconds = seconds) } }
        }
    }

    // --- Paso 1: DNI -------------------------------------------------------------------------

    fun onDniChange(value: String) = _uiState.update {
        it.copy(dni = value.filter(Char::isDigit).take(MAX_DNI_DIGITS), dniProblem = null, requestError = null)
    }

    /** Valida el DNI y pide el código. El 202 es idéntico exista o no la cuenta. */
    fun requestCode() {
        val problem = CredentialRules.problemOf(CredentialField.DNI, SignUpForm(dni = _uiState.value.dni))
        if (problem != null) {
            _uiState.update { it.copy(dniProblem = problem) }
            return
        }
        sendCodeRequest(navigateOnSuccess = true)
    }

    // --- Paso 2: código ----------------------------------------------------------------------

    fun onCodeChange(raw: String) = _uiState.update {
        it.copy(code = EmailCodeRules.sanitize(raw), codeError = null)
    }

    /** "Reenviar": sólo con la cuenta regresiva en cero. El servidor aplica su propio throttle. */
    fun resend() {
        if (!_uiState.value.canResend) return
        _uiState.update { it.copy(code = "") }
        sendCodeRequest(navigateOnSuccess = false)
    }

    fun confirmCode() {
        val state = _uiState.value
        if (!state.canConfirmCode) return
        if (!inFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(confirming = true, codeError = null) }
                val result = repository.confirmCode(state.dni, state.code)
                if (result is CodeConfirmResult.Confirmed) resetToken = result.token
                _uiState.update { current ->
                    when (result) {
                        is CodeConfirmResult.Confirmed -> current.copy(confirming = false, code = "")
                        CodeConfirmResult.InvalidCode ->
                            current.copy(confirming = false, codeError = CodeFlowError.InvalidCode)
                        CodeConfirmResult.InvalidInput ->
                            current.copy(confirming = false, codeError = CodeFlowError.InvalidInput)
                        is CodeConfirmResult.Failed ->
                            current.copy(confirming = false, codeError = CodeFlowError.Failure(result.failure))
                    }
                }
                if (result is CodeConfirmResult.Confirmed) _events.send(RecoveryEvent.CodeConfirmed)
            } finally {
                inFlight.set(false)
            }
        }
    }

    // --- Paso 3: contraseña nueva ------------------------------------------------------------

    fun onPasswordChange(value: String) = _uiState.update {
        it.copy(password = value, passwordProblems = it.passwordProblems - CredentialField.PASSWORD)
    }

    fun onPasswordConfirmationChange(value: String) = _uiState.update {
        it.copy(
            passwordConfirmation = value,
            passwordProblems = it.passwordProblems - CredentialField.PASSWORD_CONFIRMATION,
        )
    }

    /** Valida la contraseña nueva con las reglas del alta; `true` habilita pasar a la captura. */
    fun validatePassword(): Boolean {
        val state = _uiState.value
        val form = SignUpForm(password = state.password, passwordConfirmation = state.passwordConfirmation)
        val problems = listOf(CredentialField.PASSWORD, CredentialField.PASSWORD_CONFIRMATION)
            .mapNotNull { field -> CredentialRules.problemOf(field, form)?.let { field to it } }
            .toMap()
        _uiState.update { it.copy(passwordProblems = problems) }
        return problems.isEmpty()
    }

    // --- Paso 4: captura y envío -------------------------------------------------------------

    /**
     * El escaneo en vivo leyó el DNI. Si no es el que se ingresó, se avisa sin llamar al backend y
     * no se guarda nada del documento. Devuelve `true` si se puede seguir con la selfie.
     */
    fun onDocumentScanned(document: DocumentIdentity): Boolean {
        if (!CompletePasswordResetUseCase.documentMatches(document.number, _uiState.value.dni)) {
            _uiState.update { it.copy(status = RecoveryStatus.Failed(RecoveryError.DniMismatch)) }
            return false
        }
        viewModelScope.launch {
            val accepted = acceptCapture { identityVerification.acceptDocument(document) }
            _uiState.update {
                if (accepted) it.copy(documentCaptured = true, status = RecoveryStatus.Idle)
                else it.copy(status = RecoveryStatus.Failed(RecoveryError.CaptureUnusable))
            }
        }
        return true
    }

    /** La selfie, con el veredicto de la prueba de vida ya resuelto. Con las dos capturas, envía. */
    fun onSelfieCaptured(image: ByteArray, livenessPassed: Boolean) {
        viewModelScope.launch {
            val accepted = acceptCapture { identityVerification.acceptSelfie(image, livenessPassed) }
            if (!accepted) {
                _uiState.update { it.copy(status = RecoveryStatus.Failed(RecoveryError.CaptureUnusable)) }
                return@launch
            }
            _uiState.update { it.copy(selfieCaptured = true) }
            submit()
        }
    }

    private suspend fun acceptCapture(accept: suspend () -> Unit): Boolean = try {
        accept()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private suspend fun submit() {
        val state = _uiState.value
        if (!state.documentCaptured || !state.selfieCaptured) return
        if (!inFlight.compareAndSet(false, true)) return

        try {
            _uiState.update { it.copy(status = RecoveryStatus.Submitting) }

            val token = resetToken
            if (token == null) {
                identityVerification.discardCaptures()
                finishWithError(RecoveryError.InvalidResetToken)
                return
            }

            val outcome = completePasswordReset(token, state.dni, state.password)
            when (outcome) {
                is CompletePasswordResetUseCase.Outcome.Finished -> handleResult(outcome.result)
                CompletePasswordResetUseCase.Outcome.DniMismatch -> finishWithError(RecoveryError.DniMismatch)
                CompletePasswordResetUseCase.Outcome.LivenessFailed -> finishWithError(RecoveryError.LivenessFailed)
                CompletePasswordResetUseCase.Outcome.CaptureUnusable -> finishWithError(RecoveryError.CaptureUnusable)
                CompletePasswordResetUseCase.Outcome.InvalidPassword -> {
                    identityVerification.discardCaptures()
                    finishWithError(RecoveryError.InvalidData)
                }
            }
        } finally {
            inFlight.set(false)
        }
    }

    private suspend fun handleResult(result: PasswordResetResult) {
        when (result) {
            PasswordResetResult.Done -> {
                // El servidor ya revocó todas las sesiones: se borra lo local (blob y clave
                // incluidos) y se avisa en Login. El token se consumió.
                resetToken = null
                sessionCleaner.clear()
                loginNotice.post(LoginNotice.PASSWORD_RESET)
                _uiState.update {
                    it.copy(
                        status = RecoveryStatus.Completed,
                        password = "",
                        passwordConfirmation = "",
                        documentCaptured = false,
                        selfieCaptured = false,
                    )
                }
                _events.send(RecoveryEvent.Completed)
            }

            PasswordResetResult.InvalidResetToken -> {
                resetToken = null
                finishWithError(RecoveryError.InvalidResetToken)
            }

            PasswordResetResult.IdentityNotVerified -> finishWithError(RecoveryError.IdentityNotVerified)
            PasswordResetResult.PasswordRejected -> finishWithError(RecoveryError.PasswordRejected)
            PasswordResetResult.InvalidData -> finishWithError(RecoveryError.InvalidData)
            is PasswordResetResult.Failed -> finishWithError(RecoveryError.Failure(result.failure))
        }
    }

    /** Las imágenes ya no existen (el envío las descarta): cualquier reintento vuelve a capturar. */
    private fun finishWithError(error: RecoveryError) {
        _uiState.update {
            it.copy(
                status = RecoveryStatus.Failed(error),
                documentCaptured = false,
                selfieCaptured = false,
            )
        }
    }

    /** Cierra el error y deja la pantalla lista para reintentar. */
    fun onErrorDismissed() {
        if (_uiState.value.status is RecoveryStatus.Failed) {
            _uiState.update { it.copy(status = RecoveryStatus.Idle) }
        }
    }

    /**
     * `INVALID_RESET_TOKEN`: el token ya no sirve. Se vuelve al primer paso, con el DNI a la vista,
     * para pedir otro código.
     */
    fun onRestartRequested() {
        resetToken = null
        identityVerification.discardCaptures()
        countdown.reset()
        _uiState.update {
            it.copy(
                code = "",
                codeRequested = false,
                codeError = null,
                requestError = null,
                documentCaptured = false,
                selfieCaptured = false,
                status = RecoveryStatus.Idle,
            )
        }
        _events.trySend(RecoveryEvent.RestartRequested)
    }

    /** Vuelve a empezar la captura: lo pendiente se descarta. */
    fun onCaptureCancelled() {
        identityVerification.discardCaptures()
        _uiState.update { it.copy(documentCaptured = false, selfieCaptured = false, status = RecoveryStatus.Idle) }
    }

    private fun sendCodeRequest(navigateOnSuccess: Boolean) {
        if (!inFlight.compareAndSet(false, true)) return
        val dni = _uiState.value.dni

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(requesting = true, requestError = null, codeError = null) }
                when (val result = repository.requestCode(dni)) {
                    CodeRequestResult.Sent -> {
                        _uiState.update { it.copy(requesting = false, codeRequested = true) }
                        countdown.start()
                        if (navigateOnSuccess) _events.send(RecoveryEvent.CodeRequested)
                    }

                    CodeRequestResult.InvalidInput -> reportRequestError(navigateOnSuccess, CodeFlowError.InvalidInput)
                    is CodeRequestResult.Failed ->
                        reportRequestError(navigateOnSuccess, CodeFlowError.Failure(result.failure))
                }
            } finally {
                inFlight.set(false)
            }
        }
    }

    /** El error se muestra donde está el usuario: en el paso del DNI o en el del código. */
    private fun reportRequestError(fromDniStep: Boolean, error: CodeFlowError) {
        _uiState.update {
            if (fromDniStep) it.copy(requesting = false, requestError = error)
            else it.copy(requesting = false, codeError = error)
        }
    }

    /** Si el grafo muere con capturas pendientes o un token vivo, no quedan en memoria. */
    override fun onCleared() {
        resetToken = null
        identityVerification.discardCaptures()
        super.onCleared()
    }

    private companion object {
        const val MAX_DNI_DIGITS = 8
    }
}
