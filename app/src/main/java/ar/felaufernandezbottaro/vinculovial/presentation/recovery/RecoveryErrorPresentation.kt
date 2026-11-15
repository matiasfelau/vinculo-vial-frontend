package ar.felaufernandezbottaro.vinculovial.presentation.recovery

import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure
import ar.felaufernandezbottaro.vinculovial.presentation.common.message

/** Qué puede hacer el usuario con el error: define el botón principal del diálogo. */
enum class RecoveryErrorAction {
    /** Repetir el escaneo del DNI y la selfie. */
    RETRY_CAPTURE,

    /** Corregir la contraseña nueva. */
    EDIT_PASSWORD,

    /** Volver al primer paso para pedir otro código. */
    REQUEST_NEW_CODE,

    /** Sólo cerrar: no hay nada que el usuario pueda corregir ahora. */
    DISMISS,
}

data class RecoveryErrorPresentation(
    val title: String,
    val message: String,
    val action: RecoveryErrorAction,
)

/**
 * Todos los mensajes son genéricos a propósito: ninguno distingue "no es la misma persona" de
 * "no se pudo verificar", información que un atacante podría usar para calibrar intentos.
 */
fun RecoveryError.presentation(): RecoveryErrorPresentation = when (this) {
    RecoveryError.DniMismatch -> RecoveryErrorPresentation(
        title = "El DNI no coincide",
        message = "El documento que escaneaste no es el del DNI que ingresaste. Revisá que sea el " +
            "tuyo e intentá de nuevo.",
        action = RecoveryErrorAction.RETRY_CAPTURE,
    )

    RecoveryError.LivenessFailed -> RecoveryErrorPresentation(
        title = "No pudimos confirmar que seas vos",
        message = "Repetí la selfie siguiendo la indicación en pantalla, mirando de frente a la " +
            "cámara y sin anteojos de sol ni gorra.",
        action = RecoveryErrorAction.RETRY_CAPTURE,
    )

    RecoveryError.CaptureUnusable -> RecoveryErrorPresentation(
        title = "No pudimos leer tu DNI",
        message = "Apoyá el documento en una superficie plana, con buena luz y sin reflejos, y " +
            "esperá a que se vea nítido. Después volvé a sacarte la selfie.",
        action = RecoveryErrorAction.RETRY_CAPTURE,
    )

    RecoveryError.IdentityNotVerified -> RecoveryErrorPresentation(
        title = RecoveryTexts.IDENTITY_NOT_VERIFIED,
        message = "Volvé a escanear el DNI y a sacarte la selfie, con buena luz y de frente a la cámara.",
        action = RecoveryErrorAction.RETRY_CAPTURE,
    )

    RecoveryError.InvalidResetToken -> RecoveryErrorPresentation(
        title = "Pedí un código nuevo",
        message = "El código que usaste ya no sirve. Pedí uno nuevo para seguir con la recuperación.",
        action = RecoveryErrorAction.REQUEST_NEW_CODE,
    )

    RecoveryError.PasswordRejected -> RecoveryErrorPresentation(
        title = "Elegí otra contraseña",
        message = "La contraseña es demasiado predecible o se parece a tus datos. Probá con una " +
            "que no uses en otro lado y que no sea fácil de adivinar.",
        action = RecoveryErrorAction.EDIT_PASSWORD,
    )

    RecoveryError.InvalidData -> RecoveryErrorPresentation(
        title = "Revisá tus datos",
        message = "Alguno de los datos ingresados no es válido. Corregilos e intentá de nuevo.",
        action = RecoveryErrorAction.EDIT_PASSWORD,
    )

    is RecoveryError.Failure -> RecoveryErrorPresentation(
        title = "No pudimos completar el cambio",
        message = failure.message(),
        action = if (failure.isRetryable()) RecoveryErrorAction.RETRY_CAPTURE else RecoveryErrorAction.DISMISS,
    )
}

/** Reintentar tiene sentido sólo si el problema es transitorio. */
private fun RequestFailure.isRetryable(): Boolean = when (this) {
    RequestFailure.NETWORK,
    RequestFailure.UNAVAILABLE,
    RequestFailure.RATE_LIMITED,
    RequestFailure.ATTESTATION_UNAVAILABLE,
    RequestFailure.UNEXPECTED,
    -> true

    RequestFailure.ATTESTATION_REJECTED,
    RequestFailure.DEVICE_NOT_TRUSTED,
    RequestFailure.CONFIGURATION,
    -> false
}

/** Textos de la recuperación (US-13). En español rioplatense, con voseo. */
object RecoveryTexts {
    const val IDENTITY_NOT_VERIFIED = "No pudimos validar tu identidad"
}
