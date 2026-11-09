package ar.felaufernandezbottaro.vinculovial.data.session

import android.content.Context
import androidx.biometric.BiometricManager

/**
 * Si el dispositivo puede desbloquear con biometría fuerte. Sólo biometría fuerte (US-12): ni el
 * PIN ni el patrón del dispositivo sirven para desbloquear la sesión.
 */
interface BiometricCapability {

    /** Hay hardware y al menos una huella (o rostro) enrolada de clase fuerte. */
    fun isStrongBiometricEnrolled(): Boolean
}

class AndroidBiometricCapability(private val context: Context) : BiometricCapability {

    override fun isStrongBiometricEnrolled(): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
}
