package ar.felaufernandezbottaro.vinculovial.domain.session

import ar.felaufernandezbottaro.vinculovial.data.push.TokenSyncManager
import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.domain.push.DeleteFcmTokenResult
import ar.felaufernandezbottaro.vinculovial.domain.push.DeleteFcmTokenUseCase
import ar.felaufernandezbottaro.vinculovial.domain.push.FcmTokenProvider
import javax.inject.Inject

/**
 * Punto de entrada desde `presentation` hacia el logout. `SessionManager` ya centraliza la
 * limpieza y la llamada a `/logout`; esto es sólo el orquestador delgado que el resto de los
 * casos de uso de esta app también expone hacia la UI.
 *
 * La baja del token FCM se dispara acá, antes de `sessionManager.logout()`: necesita el
 * Access Token todavía vigente, que `clearSession()` borra. Si falla, no bloquea nada -- un
 * logout nunca se bloquea por un fallo de red -- pero tampoco se descarta el error: queda
 * pendiente en `TokenSyncManager` para reintentar en el próximo arranque con sesión activa.
 */
class LogoutUseCase @Inject constructor(
    private val sessionManager: SessionManager,
    private val fcmTokenProvider: FcmTokenProvider,
    private val deleteFcmToken: DeleteFcmTokenUseCase,
    private val tokenSyncManager: TokenSyncManager,
) {
    suspend operator fun invoke() {
        deleteFcmTokenIfAny()
        sessionManager.logout()
    }

    private suspend fun deleteFcmTokenIfAny() {
        val token = fcmTokenProvider.getCurrentToken() ?: return

        when (deleteFcmToken(token)) {
            is DeleteFcmTokenResult.Success -> Unit
            is DeleteFcmTokenResult.Failed -> tokenSyncManager.onLogoutDeleteFailed(token)
        }
    }
}
