package ar.felaufernandezbottaro.vinculovial.presentation.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.di.EmailCodeResendSeconds
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeConfirmResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeRequestResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.EmailCodeRules
import ar.felaufernandezbottaro.vinculovial.domain.emailverification.EmailVerificationRepository
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeFlowError
import ar.felaufernandezbottaro.vinculovial.presentation.common.ResendCountdown
import dagger.hilt.android.lifecycle.HiltViewModel
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

/** Estado de la pantalla "Verificá tu email" (US-13, alta). */
data class VerifyEmailUiState(
    val email: String = "",
    val code: String = "",
    /** Ya se pidió un código para [email]: ahora toca ingresarlo. */
    val codeRequested: Boolean = false,
    val requesting: Boolean = false,
    val confirming: Boolean = false,
    val error: CodeFlowError? = null,
    /** Segundos que faltan para poder pedir otro código (`EMAIL_CODE_RESEND_SECONDS`). */
    val resendSeconds: Int = 0,
) {
    val isBusy: Boolean get() = requesting || confirming

    val canConfirm: Boolean get() = codeRequested && EmailCodeRules.isComplete(code) && !isBusy

    val canResend: Boolean get() = !isBusy && resendSeconds == 0
}

/** Lo que el `NavHost` tiene que hacer cuando el código se confirma. */
sealed interface VerifyEmailEvent {

    /** El `verification_token` va al `SignUpViewModel`, que lo guarda sólo en memoria. */
    data class Verified(val email: String, val token: String) : VerifyEmailEvent {
        override fun toString(): String = "Verified(***)"
    }
}

/**
 * Pide el código al email del formulario, lo confirma y entrega el `verification_token`. Nunca
 * guarda el token: se lo pasa a quien lo necesita por [events] y se olvida.
 *
 * El código llega por email; ni el email ni el código se loguean.
 */
@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    private val repository: EmailVerificationRepository,
    @EmailCodeResendSeconds resendSeconds: Int,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyEmailUiState())
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    private val _events = Channel<VerifyEmailEvent>(Channel.BUFFERED)
    val events: Flow<VerifyEmailEvent> = _events.receiveAsFlow()

    private val countdown = ResendCountdown(viewModelScope, resendSeconds)

    /** Barrera sincrónica contra un doble tap en "Verificar" o "Reenviar". */
    private val inFlight = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            countdown.remainingSeconds.collect { seconds -> _uiState.update { it.copy(resendSeconds = seconds) } }
        }
    }

    /**
     * Arranca la verificación para [email]. Idempotente: volver a componer la pantalla (o rotarla)
     * con el mismo email no pide otro código; con otro email, sí.
     */
    fun start(email: String) {
        if (_uiState.value.email == email && (_uiState.value.codeRequested || _uiState.value.requesting)) return
        _uiState.value = VerifyEmailUiState(email = email)
        countdown.reset()
        requestCode()
    }

    fun onCodeChange(raw: String) = _uiState.update {
        it.copy(code = EmailCodeRules.sanitize(raw), error = null)
    }

    /** "Reenviar": sólo con la cuenta regresiva en cero. El servidor aplica su propio throttle. */
    fun resend() {
        if (!_uiState.value.canResend) return
        _uiState.update { it.copy(code = "") }
        requestCode()
    }

    fun confirm() {
        val state = _uiState.value
        if (!state.canConfirm) return
        if (!inFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(confirming = true, error = null) }
                val result = repository.confirmCode(state.email, state.code)
                _uiState.update { current ->
                    when (result) {
                        is CodeConfirmResult.Confirmed -> current.copy(confirming = false, code = "")
                        CodeConfirmResult.InvalidCode ->
                            current.copy(confirming = false, error = CodeFlowError.InvalidCode)
                        CodeConfirmResult.InvalidInput ->
                            current.copy(confirming = false, error = CodeFlowError.InvalidInput)
                        is CodeConfirmResult.Failed ->
                            current.copy(confirming = false, error = CodeFlowError.Failure(result.failure))
                    }
                }
                if (result is CodeConfirmResult.Confirmed) {
                    _events.send(VerifyEmailEvent.Verified(state.email, result.token))
                }
            } finally {
                inFlight.set(false)
            }
        }
    }

    private fun requestCode() {
        if (!inFlight.compareAndSet(false, true)) return
        val email = _uiState.value.email

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(requesting = true, error = null) }
                when (val result = repository.requestCode(email)) {
                    CodeRequestResult.Sent -> {
                        _uiState.update { it.copy(requesting = false, codeRequested = true) }
                        countdown.start()
                    }

                    CodeRequestResult.InvalidInput ->
                        _uiState.update { it.copy(requesting = false, error = CodeFlowError.InvalidInput) }

                    is CodeRequestResult.Failed ->
                        _uiState.update { it.copy(requesting = false, error = CodeFlowError.Failure(result.failure)) }
                }
            } finally {
                inFlight.set(false)
            }
        }
    }
}
