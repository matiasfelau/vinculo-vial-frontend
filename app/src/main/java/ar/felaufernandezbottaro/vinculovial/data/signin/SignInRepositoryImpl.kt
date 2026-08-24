package ar.felaufernandezbottaro.vinculovial.data.signin

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoConfigurationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeMessage
import ar.felaufernandezbottaro.vinculovial.data.remote.AuthApiService
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.data.signin.dto.SignInPayloadDto
import ar.felaufernandezbottaro.vinculovial.data.signin.dto.SignInRequestDto
import ar.felaufernandezbottaro.vinculovial.di.IdentityHpkePublicKey
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInError
import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInRepository
import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInResult
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifra el payload interno contra Identity, lo publica en el Gateway y, ante un 200,
 * persiste el par de tokens que devuelve.
 *
 * Traduce el mundo HTTP a errores de dominio: `presentation` nunca ve un código de estado
 * ni una excepción de red.
 */
@Singleton
class SignInRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    private val encryptor: HpkeEncryptor,
    @IdentityHpkePublicKey private val identityPublicKey: String,
    private val tokenStore: SecureTokenStore,
    moshi: Moshi,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : SignInRepository {

    private val payloadAdapter: JsonAdapter<SignInPayloadDto> = moshi.adapter(SignInPayloadDto::class.java)

    override suspend fun signIn(credentials: SignInCredentials): SignInResult = withContext(dispatcher) {
        val encrypted = try {
            serializeAndEncrypt(credentials)
        } catch (e: CryptoConfigurationException) {
            return@withContext SignInResult.Failed(SignInError.Configuration)
        } catch (e: CryptoOperationException) {
            return@withContext SignInResult.Failed(SignInError.Unexpected)
        }

        try {
            val response = api.signIn(
                SignInRequestDto(
                    ephemeralPublicKey = encrypted.ephemeralPublicKey,
                    ciphertext = encrypted.ciphertext,
                ),
            )
            val body = response.body()

            when {
                response.isSuccessful && body != null -> {
                    tokenStore.save(SessionTokens(accessToken = body.accessToken, refreshToken = body.refreshToken))
                    SignInResult.SignedIn
                }
                // 2xx sin cuerpo sería un contrato roto del lado del backend, no algo que la
                // UI pueda explicarle al usuario de forma específica.
                response.isSuccessful -> SignInResult.Failed(SignInError.Unexpected)
                else -> SignInResult.Failed(response.code().toSignInError())
            }
        } catch (e: CancellationException) {
            // El usuario abandonó la pantalla: cancelar no es un fallo del login.
            // Va antes que RuntimeException porque hereda de IllegalStateException.
            throw e
        } catch (e: AttestationUnavailableException) {
            // Falló en el dispositivo, antes de salir a la red. Va antes que IOException
            // porque hereda de ella (se lanza desde un interceptor de OkHttp).
            SignInResult.Failed(e.reason.toSignInError())
        } catch (e: IOException) {
            // Sin conexión, DNS, timeout, TLS.
            SignInResult.Failed(SignInError.Network)
        } catch (e: RuntimeException) {
            // Respuesta ilegible o cualquier fallo inesperado del stack HTTP.
            SignInResult.Failed(SignInError.Unexpected)
        }
    }

    /**
     * Serializa el payload interno y lo cifra sin que el JSON en claro llegue a existir
     * como `String`: se escribe directo a un buffer de bytes que se puede sobreescribir.
     */
    private fun serializeAndEncrypt(credentials: SignInCredentials): HpkeMessage {
        val buffer = Buffer()
        var plaintext = ByteArray(0)
        return try {
            payloadAdapter.toJson(
                JsonWriter.of(buffer),
                SignInPayloadDto(dni = credentials.dni, password = credentials.password),
            )
            plaintext = buffer.readByteArray()
            encryptor.encrypt(plaintext, identityPublicKey)
        } finally {
            plaintext.fill(0)
            buffer.clear()
        }
    }

    private fun Int.toSignInError(): SignInError = when (this) {
        HTTP_BAD_REQUEST -> SignInError.InvalidData
        // Mensaje único y genérico: no distingue "no existe el DNI" de "contraseña
        // incorrecta" — ni acá ni en la UI (anti-enumeración).
        HTTP_UNAUTHORIZED -> SignInError.InvalidCredentials
        HTTP_FORBIDDEN -> SignInError.AttestationRejected
        else -> SignInError.Unexpected
    }

    private fun AttestationUnavailableException.Reason.toSignInError(): SignInError = when (this) {
        AttestationUnavailableException.Reason.MISCONFIGURED -> SignInError.Configuration
        AttestationUnavailableException.Reason.DEVICE_NOT_TRUSTED -> SignInError.DeviceNotTrusted
        AttestationUnavailableException.Reason.PLAY_UNAVAILABLE,
        AttestationUnavailableException.Reason.TRANSIENT,
        -> SignInError.AttestationUnavailable
    }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
