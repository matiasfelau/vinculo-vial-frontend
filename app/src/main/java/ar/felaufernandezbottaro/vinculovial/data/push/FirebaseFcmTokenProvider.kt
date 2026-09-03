package ar.felaufernandezbottaro.vinculovial.data.push

import ar.felaufernandezbottaro.vinculovial.domain.push.FcmTokenProvider
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Envuelve el SDK de Firebase detrás de [FcmTokenProvider]. El catch es amplio a propósito
 * (no sólo `RuntimeException`): sin `google-services.json` real (ver app/build.gradle.kts)
 * `FirebaseMessaging.getInstance()` lanza `IllegalStateException`, y las fallas propias del
 * SDK son subclases de `FirebaseException`, que no hereda de `RuntimeException`.
 */
@Singleton
class FirebaseFcmTokenProvider @Inject constructor() : FcmTokenProvider {

    override suspend fun getCurrentToken(): String? = try {
        FirebaseMessaging.getInstance().token.await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
