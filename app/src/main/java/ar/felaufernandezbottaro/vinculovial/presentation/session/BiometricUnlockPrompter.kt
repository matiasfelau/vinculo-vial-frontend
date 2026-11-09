package ar.felaufernandezbottaro.vinculovial.presentation.session

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * El prompt biométrico del desbloqueo (US-12): sólo `BIOMETRIC_STRONG`, con el `Cipher` de la
 * clave privada como `CryptoObject`. Sin PIN ni patrón del dispositivo como alternativa: la
 * alternativa es "Usar contraseña" en la propia pantalla de Desbloqueo.
 */
object BiometricUnlockPrompter {

    /**
     * @param onAuthenticated recibe el `Cipher` ya autorizado por la biometría: es el único que
     * puede descifrar el blob.
     */
    fun show(
        activity: FragmentActivity,
        cipher: Cipher,
        onAuthenticated: (Cipher) -> Unit,
        onFailed: (PromptFailure) -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authenticated = result.cryptoObject?.cipher
                if (authenticated != null) onAuthenticated(authenticated) else onFailed(PromptFailure.OTHER)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailed(errorCode.toPromptFailure())
            }
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(SessionTexts.PROMPT_TITLE)
            .setSubtitle(SessionTexts.PROMPT_SUBTITLE)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(SessionTexts.PROMPT_CANCEL)
            .build()

        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun Int.toPromptFailure(): PromptFailure = when (this) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED,
        -> PromptFailure.CANCELLED

        BiometricPrompt.ERROR_LOCKOUT,
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
        -> PromptFailure.LOCKED_OUT

        else -> PromptFailure.OTHER
    }
}
