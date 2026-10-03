package ar.felaufernandezbottaro.vinculovial.data.crypto

import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import ar.felaufernandezbottaro.vinculovial.BuildConfig
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException.Reason
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attestation real contra Google Play Integrity, usando la Standard Request.
 *
 * El token que devuelve es opaco para la app: lo emite Google y sólo el backend puede
 * decodificarlo, llamando a la Play Integrity API con una cuenta de servicio del mismo
 * proyecto de Cloud. Es decir: en este modo el Gateway ya no valida un HMAC propio.
 *
 * Cada token se liga al cuerpo de la request vía `requestHash`, así un token capturado
 * no sirve para firmar otro registro.
 *
 * [integrityManager] se recibe armado (no se construye acá con `IntegrityManagerFactory`,
 * ver `CryptoModule`): es la única forma de reemplazarlo por un fake de mano en tests --
 * tanto [StandardIntegrityManager] como [StandardIntegrityTokenProvider] son interfaces
 * del SDK, así que un objeto que las implemente alcanza, sin mockk ni Robolectric.
 */
@Singleton
class PlayIntegrityAttestationProvider @Inject constructor(
    private val integrityManager: StandardIntegrityManager,
    private val cloudProjectNumber: Long,
) : AttestationProvider {

    /**
     * Preparar el proveedor es caro (habla con Play Services), así que se cachea.
     * Google lo invalida cada tanto; ahí se vuelve a preparar solo.
     */
    private val preparationLock = Mutex()
    private var tokenProvider: StandardIntegrityTokenProvider? = null

    override suspend fun tokenFor(requestBody: ByteArray): String {
        if (cloudProjectNumber <= 0L) {
            throw AttestationUnavailableException(
                message = "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER no está configurado",
                reason = Reason.MISCONFIGURED,
            )
        }

        val requestHash = requestHashOf(requestBody)

        return try {
            // Un único presupuesto para el intento Y el reintento juntos, no uno cada uno --
            // si no, un proveedor que se invalida justo antes de expirar el timeout consumiría
            // el doble del `ATTESTATION_TIMEOUT_MS` declarado. Ver la invariante
            // `callTimeout > ATTESTATION_TIMEOUT_MS + connect + read` en NetworkModule: el
            // Task de Play corre antes de abrir el socket, así que ni `connectTimeout` ni
            // `readTimeout` de OkHttp lo cubren.
            withTimeout(BuildConfig.ATTESTATION_TIMEOUT_MS) {
                requestTokenWithRetry(requestHash)
            }
        } catch (e: TimeoutCancellationException) {
            // El `Task` de Play (prepare o request, según en cuál se haya colgado) nunca
            // llamó a su callback.
            throw e.asAttestationTimeout()
        }
    }

    /** Un intento y, si el proveedor cacheado había caducado, exactamente un reintento. */
    private suspend fun requestTokenWithRetry(requestHash: String): String = try {
        requestToken(provider = obtainProvider(), requestHash = requestHash)
    } catch (e: StandardIntegrityException) {
        if (e.errorCode == StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID) {
            invalidateProvider()
            try {
                requestToken(provider = obtainProvider(), requestHash = requestHash)
            } catch (e2: StandardIntegrityException) {
                throw e2.asAttestationFailure()
            }
        } else {
            throw e.asAttestationFailure()
        }
    }

    private suspend fun requestToken(
        provider: StandardIntegrityTokenProvider,
        requestHash: String,
    ): String = provider
        .request(StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build())
        .await()
        .token()

    private suspend fun obtainProvider(): StandardIntegrityTokenProvider {
        tokenProvider?.let { return it }

        return preparationLock.withLock {
            // Otra corrutina pudo haberlo preparado mientras esperábamos el lock.
            tokenProvider ?: prepareProvider().also { tokenProvider = it }
        }
    }

    private suspend fun prepareProvider(): StandardIntegrityTokenProvider = try {
        integrityManager
            .prepareIntegrityToken(
                PrepareIntegrityTokenRequest.builder()
                    .setCloudProjectNumber(cloudProjectNumber)
                    .build(),
            )
            .await()
    } catch (e: StandardIntegrityException) {
        throw e.asAttestationFailure()
    }

    private suspend fun invalidateProvider() = preparationLock.withLock { tokenProvider = null }

    /**
     * Traduce el código de Google a una causa que la UI pueda explicar.
     * El mensaje nunca incluye datos del usuario, sólo el código.
     */
    private fun StandardIntegrityException.asAttestationFailure(): AttestationUnavailableException {
        val reason = when (errorCode) {
            StandardIntegrityErrorCode.API_NOT_AVAILABLE,
            StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND,
            StandardIntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED,
            StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND,
            StandardIntegrityErrorCode.PLAY_SERVICES_VERSION_OUTDATED,
            StandardIntegrityErrorCode.CANNOT_BIND_TO_SERVICE,
            -> Reason.PLAY_UNAVAILABLE

            // La app no es una instalación reconocida por Play (sideload, repackaging).
            StandardIntegrityErrorCode.APP_NOT_INSTALLED,
            StandardIntegrityErrorCode.APP_UID_MISMATCH,
            -> Reason.DEVICE_NOT_TRUSTED

            StandardIntegrityErrorCode.CLOUD_PROJECT_NUMBER_IS_INVALID,
            StandardIntegrityErrorCode.REQUEST_HASH_TOO_LONG,
            -> Reason.MISCONFIGURED

            // NETWORK_ERROR, TOO_MANY_REQUESTS, GOOGLE_SERVER_UNAVAILABLE,
            // CLIENT_TRANSIENT_ERROR, INTERNAL_ERROR y cualquier código nuevo.
            else -> Reason.TRANSIENT
        }

        return AttestationUnavailableException(
            message = "Play Integrity rechazó la solicitud (código $errorCode)",
            reason = reason,
            cause = this,
        )
    }

    /**
     * A diferencia de [asAttestationFailure], acá Google no devolvió ningún código: el
     * `Task` sencillamente no llamó a su callback dentro del plazo. Se trata como
     * [Reason.TRANSIENT] -- mismo criterio que NETWORK_ERROR o GOOGLE_SERVER_UNAVAILABLE --
     * porque no hay evidencia de que el dispositivo o la instalación sean el problema.
     */
    private fun TimeoutCancellationException.asAttestationTimeout(): AttestationUnavailableException =
        AttestationUnavailableException(
            message = "Play Integrity no respondió dentro del tiempo esperado",
            reason = Reason.TRANSIENT,
            cause = this,
        )
}
