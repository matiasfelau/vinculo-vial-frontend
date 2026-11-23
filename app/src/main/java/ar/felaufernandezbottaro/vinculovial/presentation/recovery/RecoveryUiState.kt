package ar.felaufernandezbottaro.vinculovial.presentation.recovery

import ar.felaufernandezbottaro.vinculovial.domain.auth.EmailCodeRules
import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialField
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialProblem
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeFlowError

/**
 * Estado único del grafo `recovery` (US-13). Nunca contiene el `reset_token`, bytes de imagen ni
 * los datos del documento: el token vive en el ViewModel y las capturas en `data/edge`, que las
 * descarta apenas termina el envío (Zero Retention).
 */
data class RecoveryUiState(
    val dni: String = "",
    val dniProblem: CredentialProblem? = null,
    val code: String = "",
    /** Se pidió un código para [dni]: la pantalla del código muestra el aviso y la cuenta regresiva. */
    val codeRequested: Boolean = false,
    val requesting: Boolean = false,
    val confirming: Boolean = false,
    /** Error del paso del DNI (pedir el código). */
    val requestError: CodeFlowError? = null,
    /** Error del paso del código (confirmarlo o reenviarlo). */
    val codeError: CodeFlowError? = null,
    val resendSeconds: Int = 0,
    val password: String = "",
    val passwordConfirmation: String = "",
    val passwordProblems: Map<CredentialField, CredentialProblem> = emptyMap(),
    val documentCaptured: Boolean = false,
    val selfieCaptured: Boolean = false,
    val status: RecoveryStatus = RecoveryStatus.Idle,
) {
    val isCodeBusy: Boolean get() = requesting || confirming

    val canConfirmCode: Boolean get() = codeRequested && EmailCodeRules.isComplete(code) && !isCodeBusy

    val canResend: Boolean get() = !isCodeBusy && resendSeconds == 0

    val isSubmitting: Boolean get() = status is RecoveryStatus.Submitting

    val error: RecoveryError? get() = (status as? RecoveryStatus.Failed)?.error

    override fun toString(): String = "RecoveryUiState(***)"
}

sealed interface RecoveryStatus {
    data object Idle : RecoveryStatus

    /** Verificación local, HPKE, attestation y POST en curso. */
    data object Submitting : RecoveryStatus

    /** 204: la contraseña cambió. */
    data object Completed : RecoveryStatus

    data class Failed(val error: RecoveryError) : RecoveryStatus
}

/** Lo que puede salir mal al cerrar la recuperación, visto desde la pantalla. */
sealed interface RecoveryError {

    /** El DNI del documento no es el que se ingresó. No se llamó al backend. */
    data object DniMismatch : RecoveryError

    /** La prueba de vida no se superó. */
    data object LivenessFailed : RecoveryError

    /** Falta una captura o la selfie quedó inservible. */
    data object CaptureUnusable : RecoveryError

    /** `identidad_no_verificada`. Cada rechazo descuenta un intento del token. */
    data object IdentityNotVerified : RecoveryError

    /** `INVALID_RESET_TOKEN`: hay que volver a pedir el código. */
    data object InvalidResetToken : RecoveryError

    /** La contraseña es débil o predecible según el servidor. */
    data object PasswordRejected : RecoveryError

    /** `datos_invalidos` genérico. */
    data object InvalidData : RecoveryError

    data class Failure(val failure: RequestFailure) : RecoveryError
}
