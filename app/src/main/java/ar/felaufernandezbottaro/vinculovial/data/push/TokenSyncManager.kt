package ar.felaufernandezbottaro.vinculovial.data.push

import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionState
import ar.felaufernandezbottaro.vinculovial.di.ApplicationScope
import ar.felaufernandezbottaro.vinculovial.domain.push.DeleteFcmTokenResult
import ar.felaufernandezbottaro.vinculovial.domain.push.DeleteFcmTokenUseCase
import ar.felaufernandezbottaro.vinculovial.domain.push.FcmTokenProvider
import ar.felaufernandezbottaro.vinculovial.domain.push.SyncFcmTokenResult
import ar.felaufernandezbottaro.vinculovial.domain.push.SyncFcmTokenUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquesta los tres momentos de sincronización del token FCM (ver CLAUDE.md), pero sólo expone
 * dos entradas: `sessionState` de [SecureTokenStore] ya cubre "login exitoso" y "arranque con
 * sesión activa" con un único mecanismo reactivo -- es un `StateFlow`, así que el primer
 * colector recibe el valor vigente al suscribirse (arranque) y cada transición posterior a
 * `UNLOCKED` (login o desbloqueo). Un refresh de token no dispara una re-sincronización
 * espuria porque `rotate()` deja el valor en `UNLOCKED` de nuevo y `StateFlow` sólo emite ante
 * un cambio real.
 *
 * Con la sesión bloqueada (US-12) no hay access token, así que el trabajo en segundo plano que
 * lo necesita queda diferido hasta `UNLOCKED`: sólo ese estado dispara la sincronización.
 *
 * `onTokenRotated` sigue siendo un método aparte: `onNewToken` es un evento propio de Firebase
 * que no encaja en ese stream.
 *
 * Este manager se inyecta como campo en `VinculoVialApplication` únicamente para forzar su
 * construcción -- y con ella, este `init` -- apenas arranca el proceso.
 */
@Singleton
class TokenSyncManager @Inject constructor(
    private val tokenStore: SecureTokenStore,
    private val pendingStore: PendingFcmTokenStore,
    private val syncFcmToken: SyncFcmTokenUseCase,
    private val deleteFcmToken: DeleteFcmTokenUseCase,
    private val fcmTokenProvider: FcmTokenProvider,
    @ApplicationScope private val scope: CoroutineScope,
) {

    init {
        scope.launch {
            tokenStore.sessionState.filter { it == SessionState.UNLOCKED }.collect { syncCurrentToken() }
        }
    }

    /** `onNewToken` puede dispararse sin sesión activa: acá es donde eso se decide. */
    fun onTokenRotated(token: String) {
        scope.launch { syncOrQueue(token) }
    }

    /** `LogoutUseCase` llama a esto cuando la baja del token falla, para no perderla. */
    fun onLogoutDeleteFailed(token: String) {
        pendingStore.savePendingDeletion(token)
    }

    private suspend fun syncCurrentToken() {
        retryPendingDeletion()

        val token = pendingStore.readPendingRegistration() ?: fcmTokenProvider.getCurrentToken() ?: return
        syncOrQueue(token)
    }

    /**
     * "El token pendiente de baja se registra para reintentar en el próximo arranque" -- este
     * es ese próximo arranque (o, dentro del mismo proceso, cualquier sesión que vuelva a
     * activarse). La baja exige un Access Token válido, así que no tiene sentido reintentarla
     * fuera de este flujo reactivo, atado a `sessionState`.
     */
    private suspend fun retryPendingDeletion() {
        val pending = pendingStore.readPendingDeletion() ?: return
        when (deleteFcmToken(pending)) {
            is DeleteFcmTokenResult.Success -> pendingStore.clearPendingDeletion()
            is DeleteFcmTokenResult.Failed -> Unit
        }
    }

    private suspend fun syncOrQueue(token: String) {
        if (!tokenStore.hasSession()) {
            // Sin sesión el POST fallaría con 401: ni se intenta, se encola directo para el
            // próximo login. Intentarlo y descartar el error dejaría el dispositivo
            // desincronizado en silencio.
            pendingStore.savePendingRegistration(token)
            return
        }

        when (syncFcmToken(token)) {
            is SyncFcmTokenResult.Success -> pendingStore.clearPendingRegistration()
            is SyncFcmTokenResult.Failed -> pendingStore.savePendingRegistration(token)
        }
    }
}
