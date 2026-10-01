package ar.felaufernandezbottaro.vinculovial.data.push

import ar.felaufernandezbottaro.vinculovial.BuildConfig
import ar.felaufernandezbottaro.vinculovial.domain.push.FcmTokenProvider
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * El único método de `FirebaseMessaging` que esta clase necesita. `FirebaseMessaging` es una
 * clase concreta con constructor no público y un `getInstance()` estático: sin este
 * indirection no hay forma de sustituirla por un fake de mano en tests, ni mockk ni
 * Robolectric mediante -- ver `CryptoModule`/`PlayIntegrityAttestationProvider` para el mismo
 * criterio aplicado al SDK de Play Integrity.
 */
fun interface FcmTokenTaskSource {
    fun currentTokenTask(): Task<String>
}

/**
 * Envuelve el SDK de Firebase detrás de [FcmTokenProvider]. El catch es amplio a propósito
 * (no sólo `RuntimeException`): sin `google-services.json` real (ver app/build.gradle.kts)
 * `FirebaseMessaging.getInstance()` lanza `IllegalStateException`, y las fallas propias del
 * SDK son subclases de `FirebaseException`, que no hereda de `RuntimeException`.
 *
 * `withTimeout` acota el `Task` de Firebase -- mismo problema que el de Play Integrity (ver
 * `PlayIntegrityAttestationProvider`): sin esto, un `Task` que nunca llama a su callback
 * dejaba este método (y a quien lo espera: sincronización de token post-login, logout)
 * colgado sin límite, ya que acá no hay ningún `OkHttpClient` cuyo `callTimeout` lo cubra --
 * esto nunca sale a la red de esta app, es un SDK de Google aparte. Reutiliza
 * `NETWORK_TIMEOUT_MS`, no `ATTESTATION_TIMEOUT_MS`: ese otro presupuesto es específico de
 * la cadena `callTimeout` de `NetworkModule`, que esta llamada no integra.
 *
 * El timeout llega como [TimeoutCancellationException], subclase de [CancellationException]:
 * tiene que atraparse *antes* que el catch genérico de cancelación, o esta función
 * terminaría relanzando su propio timeout interno en vez de tratarlo como "token no
 * disponible" -- el mismo resultado que cualquier otro fallo de este método.
 */
@Singleton
class FirebaseFcmTokenProvider @Inject constructor(
    private val tokenSource: FcmTokenTaskSource,
) : FcmTokenProvider {

    override suspend fun getCurrentToken(): String? = try {
        withTimeout(BuildConfig.NETWORK_TIMEOUT_MS) {
            tokenSource.currentTokenTask().await()
        }
    } catch (e: TimeoutCancellationException) {
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
