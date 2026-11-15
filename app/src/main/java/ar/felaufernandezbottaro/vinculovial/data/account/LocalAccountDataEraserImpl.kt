package ar.felaufernandezbottaro.vinculovial.data.account

import android.util.Log
import ar.felaufernandezbottaro.vinculovial.data.push.PendingFcmTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.domain.account.LocalAccountDataEraser
import ar.felaufernandezbottaro.vinculovial.domain.push.FcmTokenProvider
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lo que la app guarda del usuario en el dispositivo, y que la baja de cuenta (US-11) borra:
 * - la sesión: los tokens en memoria y, si la biometría estaba activada, el blob del refresh
 *   token y la clave del Keystore que lo envolvía;
 * - el token FCM del dispositivo, y los pendientes de alta o baja.
 *
 * Del buzón no hay nada que borrar acá: la app no lo guarda en disco ni en caché (las alertas
 * y las fotos viven sólo en memoria de sus ViewModels, que mueren con la navegación a la
 * bienvenida).
 *
 * Cada paso es independiente. La cuenta ya no existe del lado del servidor, así que un paso
 * que falla no puede ni bloquear a los demás ni convertirse en un error para el usuario: se
 * registra, sin ningún dato del usuario, y se sigue.
 */
@Singleton
class LocalAccountDataEraserImpl @Inject constructor(
    private val sessionManager: SessionManager,
    private val fcmTokenProvider: FcmTokenProvider,
    private val pendingFcmTokenStore: PendingFcmTokenStore,
) : LocalAccountDataEraser {

    override suspend fun eraseAll() {
        // La sesión se borra primero y sin depender de nada más: es lo que saca al usuario de
        // ella. Los tokens, el blob y la clave biométrica se borran dentro del mismo lock, ver
        // SessionManager.
        step("sesion") { sessionManager.clearSessionForAccountDeletion() }
        step("token fcm") {
            if (!fcmTokenProvider.deleteCurrentToken()) Log.w(TAG, "No se pudo invalidar el token FCM")
        }
        step("pendientes de push") { pendingFcmTokenStore.clearAll() }
    }

    private suspend fun step(name: String, action: suspend () -> Unit) {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sólo el tipo de la excepción: el mensaje de una falla de Keystore o de Firebase
            // no tiene por qué ser seguro de loguear.
            Log.w(TAG, "Falló el paso '$name' de la limpieza local: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "LocalAccountDataEraser"
    }
}
