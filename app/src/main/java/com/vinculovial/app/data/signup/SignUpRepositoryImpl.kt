package com.vinculovial.app.data.signup

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.vinculovial.app.data.crypto.AttestationUnavailableException
import com.vinculovial.app.data.crypto.CryptoConfigurationException
import com.vinculovial.app.data.crypto.CryptoOperationException
import com.vinculovial.app.data.crypto.HpkeEncryptor
import com.vinculovial.app.data.crypto.HpkeMessage
import com.vinculovial.app.data.remote.AuthApiService
import com.vinculovial.app.data.signup.dto.SignUpPayloadDto
import com.vinculovial.app.data.signup.dto.SignUpRequestDto
import com.vinculovial.app.di.IoDispatcher
import com.vinculovial.app.domain.signup.SignUpError
import com.vinculovial.app.domain.signup.SignUpRepository
import com.vinculovial.app.domain.signup.SignUpResult
import com.vinculovial.app.domain.signup.model.Credentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifra el payload interno contra Identity y lo publica en el Gateway.
 *
 * Traduce el mundo HTTP a errores de dominio: `presentation` nunca ve un código
 * de estado ni una excepción de red.
 */
@Singleton
class SignUpRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    private val encryptor: HpkeEncryptor,
    moshi: Moshi,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : SignUpRepository {

    private val payloadAdapter: JsonAdapter<SignUpPayloadDto> = moshi.adapter(SignUpPayloadDto::class.java)

    override suspend fun signUp(credentials: Credentials): SignUpResult = withContext(dispatcher) {
        val encrypted = try {
            serializeAndEncrypt(credentials)
        } catch (e: CryptoConfigurationException) {
            return@withContext SignUpResult.Failed(SignUpError.Configuration)
        } catch (e: CryptoOperationException) {
            return@withContext SignUpResult.Failed(SignUpError.Unexpected)
        }

        try {
            val response = api.signUp(
                SignUpRequestDto(
                    ephemeralPublicKey = encrypted.ephemeralPublicKey,
                    ciphertext = encrypted.ciphertext,
                ),
            )

            // El contrato responde 201; cualquier 2xx significa que la cuenta quedó creada.
            if (response.isSuccessful) {
                SignUpResult.Registered
            } else {
                SignUpResult.Failed(response.code().toSignUpError())
            }
        } catch (e: CancellationException) {
            // El usuario abandonó la pantalla: cancelar no es un fallo del registro.
            // Va antes que RuntimeException porque hereda de IllegalStateException.
            throw e
        } catch (e: AttestationUnavailableException) {
            // Falló en el dispositivo, antes de salir a la red. Va antes que IOException
            // porque hereda de ella (se lanza desde un interceptor de OkHttp).
            SignUpResult.Failed(e.reason.toSignUpError())
        } catch (e: IOException) {
            // Sin conexión, DNS, timeout, TLS.
            SignUpResult.Failed(SignUpError.Network)
        } catch (e: RuntimeException) {
            // Respuesta ilegible o cualquier fallo inesperado del stack HTTP.
            SignUpResult.Failed(SignUpError.Unexpected)
        }
    }

    /**
     * Serializa el payload interno y lo cifra sin que el JSON en claro llegue a existir
     * como `String`: se escribe directo a un buffer de bytes que se puede sobreescribir.
     *
     * Límite conocido: la contraseña sigue viva como `String` dentro de [Credentials]
     * hasta que pase el GC. Eliminarlo exigiría `CharArray` desde el propio TextField
     * de Compose, que no expone esa API.
     */
    private fun serializeAndEncrypt(credentials: Credentials): HpkeMessage {
        val buffer = Buffer()
        var plaintext = ByteArray(0)
        return try {
            payloadAdapter.toJson(
                JsonWriter.of(buffer),
                SignUpPayloadDto(
                    dni = credentials.dni,
                    email = credentials.email,
                    password = credentials.password,
                    biometricVerified = credentials.biometricVerified,
                ),
            )
            plaintext = buffer.readByteArray()
            encryptor.encrypt(plaintext)
        } finally {
            plaintext.fill(0)
            buffer.clear()
        }
    }

    private fun Int.toSignUpError(): SignUpError = when (this) {
        HTTP_BAD_REQUEST -> SignUpError.InvalidData
        HTTP_FORBIDDEN -> SignUpError.AttestationRejected
        HTTP_CONFLICT -> SignUpError.AlreadyRegistered
        else -> SignUpError.Unexpected
    }

    private fun AttestationUnavailableException.Reason.toSignUpError(): SignUpError = when (this) {
        AttestationUnavailableException.Reason.MISCONFIGURED -> SignUpError.Configuration
        AttestationUnavailableException.Reason.DEVICE_NOT_TRUSTED -> SignUpError.DeviceNotTrusted
        AttestationUnavailableException.Reason.PLAY_UNAVAILABLE,
        AttestationUnavailableException.Reason.TRANSIENT,
        -> SignUpError.AttestationUnavailable
    }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_FORBIDDEN = 403
        const val HTTP_CONFLICT = 409
    }
}
