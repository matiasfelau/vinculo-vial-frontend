package com.vinculovial.app.data.crypto

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import com.vinculovial.app.data.crypto.AttestationUnavailableException.Reason
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
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
 */
@Singleton
class PlayIntegrityAttestationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
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
            requestToken(provider = obtainProvider(), requestHash = requestHash)
        } catch (e: StandardIntegrityException) {
            if (e.errorCode == StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID) {
                // El proveedor cacheado caducó: se descarta y se reintenta una vez.
                invalidateProvider()
                retryOnce(requestHash)
            } else {
                throw e.asAttestationFailure()
            }
        }
    }

    private suspend fun retryOnce(requestHash: String): String = try {
        requestToken(provider = obtainProvider(), requestHash = requestHash)
    } catch (e: StandardIntegrityException) {
        throw e.asAttestationFailure()
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
        IntegrityManagerFactory.createStandard(context)
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
}
