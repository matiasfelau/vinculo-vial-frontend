package ar.felaufernandezbottaro.vinculovial.domain.session

import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.data.session.UnlockOutcome
import ar.felaufernandezbottaro.vinculovial.data.session.UnlockPreparation
import javax.crypto.Cipher
import javax.inject.Inject

/**
 * Desbloqueo de la sesión con la biometría (US-12), en dos pasos porque el prompt biométrico
 * va en el medio:
 * 1. [prepare] arma el `Cipher` que se le entrega al prompt;
 * 2. [invoke] recibe ese mismo `Cipher`, ya autenticado, y recupera la sesión.
 *
 * Cuando el desbloqueo termina en Login sin que el usuario lo haya pedido, deja en
 * [LoginNoticeStore] el motivo para que Login lo muestre.
 */
class UnlockSessionUseCase @Inject constructor(
    private val sessionManager: SessionManager,
    private val loginNotice: LoginNoticeStore,
) {

    suspend fun prepare(): UnlockPreparation {
        val preparation = sessionManager.prepareUnlock()
        if (preparation is UnlockPreparation.KeyInvalidated) loginNotice.post(LoginNotice.BIOMETRICS_CHANGED)
        return preparation
    }

    suspend operator fun invoke(cipher: Cipher): UnlockOutcome {
        val outcome = sessionManager.unlock(cipher)
        if (outcome is UnlockOutcome.Expired) loginNotice.post(LoginNotice.SESSION_EXPIRED)
        return outcome
    }
}
