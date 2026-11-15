package ar.felaufernandezbottaro.vinculovial.domain.session

import ar.felaufernandezbottaro.vinculovial.data.session.BiometricCapability
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Activar y desactivar el desbloqueo con biometría (US-12), desde el ofrecimiento tras el login o
 * desde Settings.
 *
 * Sólo biometría fuerte: si el dispositivo no tiene una huella (o rostro) fuerte enrolada, no se
 * ofrece ni se puede activar.
 */
class BiometricUnlockSettings @Inject constructor(
    private val sessionManager: SessionManager,
    private val tokenStore: SecureTokenStore,
    private val capability: BiometricCapability,
) {

    /** El desbloqueo con biometría está activado. */
    val isEnabled: StateFlow<Boolean> = tokenStore.biometricEnabled

    /** Corresponde mostrar el ofrecimiento: recién se entró con la contraseña, hay biometría fuerte y no está activada. */
    val shouldOffer: Flow<Boolean> = combine(tokenStore.biometricOfferPending, tokenStore.biometricEnabled) { pending, enabled ->
        pending && !enabled && capability.isStrongBiometricEnrolled()
    }

    fun isAvailable(): Boolean = capability.isStrongBiometricEnrolled()

    /** `false` si el dispositivo no pudo crear la clave: queda todo como antes. */
    suspend fun enable(): Boolean = sessionManager.enableBiometric()

    suspend fun disable() = sessionManager.disableBiometric()

    /** El usuario rechazó el ofrecimiento: no vuelve a aparecer hasta el próximo login. */
    fun dismissOffer() = tokenStore.dismissBiometricOffer()
}
