package ar.felaufernandezbottaro.vinculovial.presentation.session

import androidx.biometric.BiometricPrompt

/** Traduce el código de error de `BiometricPrompt` a lo que le importa al desbloqueo. */
internal fun Int.toPromptFailure(): PromptFailure = when (this) {
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_CANCELED,
    -> PromptFailure.CANCELLED

    BiometricPrompt.ERROR_LOCKOUT,
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
    -> PromptFailure.LOCKED_OUT

    else -> PromptFailure.OTHER
}
