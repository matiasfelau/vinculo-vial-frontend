package ar.felaufernandezbottaro.vinculovial.data.remote.auth

import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoConfigurationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeMessage
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.AuthErrorBodyDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.HpkeEnvelopeDto
import ar.felaufernandezbottaro.vinculovial.data.remote.isTransientUpstreamFailure
import ar.felaufernandezbottaro.vinculovial.di.IdentityHpkePublicKey
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Buffer
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que pasó al llamar a un endpoint público de `/auth`, ya clasificado. */
sealed interface AuthCallOutcome<out T> {

    /** 2xx. [body] es `null` cuando el endpoint no devuelve cuerpo (202, 204). */
    data class Success<T>(val body: T?) : AuthCallOutcome<T>

    /**
     * 400 con el cuerpo del contrato. Los errores se mapean por `code`, no por el texto del
     * mensaje. [body] es `null` si el cuerpo vino vacío o ilegible.
     */
    data class Rejected(val body: AuthErrorBodyDto?) : AuthCallOutcome<Nothing> {
        val code: String? get() = body?.code
    }

    data class Failed(val failure: RequestFailure) : AuthCallOutcome<Nothing>
}

/**
 * Cifra un payload con HPKE contra la clave pública de Identity (`info` vacío, igual que el
 * signup y el signin), lo publica en el Gateway y clasifica la respuesta. Es lo que comparten la
 * verificación del email y la recuperación de contraseña de US-13.
 *
 * El payload en claro nunca llega a existir como `String`: se escribe directo a un buffer de
 * bytes que se sobreescribe al terminar. Ni el payload, ni el cuerpo de la respuesta ni los
 * códigos se loguean.
 */
@Singleton
class SealedAuthCaller @Inject constructor(
    private val encryptor: HpkeEncryptor,
    @IdentityHpkePublicKey private val identityPublicKey: String,
    private val moshi: Moshi,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private val errorAdapter = moshi.adapter(AuthErrorBodyDto::class.java)

    suspend fun <P : Any, R : Any> call(
        payload: P,
        payloadType: Class<P>,
        send: suspend (HpkeEnvelopeDto) -> Response<R>,
    ): AuthCallOutcome<R> = withContext(dispatcher) {
        val sealed = try {
            seal(payload, payloadType)
        } catch (e: CryptoConfigurationException) {
            return@withContext AuthCallOutcome.Failed(RequestFailure.CONFIGURATION)
        } catch (e: CryptoOperationException) {
            return@withContext AuthCallOutcome.Failed(RequestFailure.UNEXPECTED)
        }

        try {
            val response = send(HpkeEnvelopeDto(sealed.ephemeralPublicKey, sealed.ciphertext))
            classify(response)
        } catch (e: CancellationException) {
            // El usuario abandonó la pantalla: cancelar no es un fallo. Va antes que
            // RuntimeException porque hereda de IllegalStateException.
            throw e
        } catch (e: AttestationUnavailableException) {
            // Falló en el dispositivo, antes de salir a la red. Va antes que IOException
            // porque hereda de ella (se lanza desde un interceptor de OkHttp).
            AuthCallOutcome.Failed(e.reason.toFailure())
        } catch (e: CircuitBreakerOpenException) {
            AuthCallOutcome.Failed(RequestFailure.UNAVAILABLE)
        } catch (e: JsonEncodingException) {
            // La respuesta no es JSON: hereda de IOException, pero no es un problema de red.
            AuthCallOutcome.Failed(RequestFailure.UNEXPECTED)
        } catch (e: IOException) {
            AuthCallOutcome.Failed(RequestFailure.NETWORK)
        } catch (e: RuntimeException) {
            AuthCallOutcome.Failed(RequestFailure.UNEXPECTED)
        }
    }

    private fun <P : Any> seal(payload: P, payloadType: Class<P>): HpkeMessage {
        val buffer = Buffer()
        var plaintext = ByteArray(0)
        return try {
            moshi.adapter(payloadType).toJson(JsonWriter.of(buffer), payload)
            plaintext = buffer.readByteArray()
            encryptor.encrypt(plaintext, identityPublicKey)
        } finally {
            plaintext.fill(0)
            buffer.clear()
        }
    }

    private fun <R : Any> classify(response: Response<R>): AuthCallOutcome<R> {
        val status = response.code()
        return when {
            response.isSuccessful -> AuthCallOutcome.Success(response.body())
            status == HTTP_BAD_REQUEST -> AuthCallOutcome.Rejected(parseError(response))
            // El 429 lo manda Kong (rate limit) sin el cuerpo del contrato: no tiene `code`.
            status == HTTP_TOO_MANY_REQUESTS -> AuthCallOutcome.Failed(RequestFailure.RATE_LIMITED)
            status == HTTP_FORBIDDEN -> AuthCallOutcome.Failed(RequestFailure.ATTESTATION_REJECTED)
            // El 504 nativo de Kong tampoco trae cuerpo del contrato: se clasifica por status.
            status.isTransientUpstreamFailure() -> AuthCallOutcome.Failed(RequestFailure.UNAVAILABLE)
            else -> AuthCallOutcome.Failed(RequestFailure.UNEXPECTED)
        }
    }

    private fun parseError(response: Response<*>): AuthErrorBodyDto? {
        val raw = try {
            response.errorBody()?.string()
        } catch (e: IOException) {
            null
        } ?: return null
        return try {
            errorAdapter.fromJson(raw)
        } catch (e: Exception) {
            // Un cuerpo mal formado no debe tumbar el manejo del error en sí.
            null
        }
    }

    private fun AttestationUnavailableException.Reason.toFailure(): RequestFailure = when (this) {
        AttestationUnavailableException.Reason.MISCONFIGURED -> RequestFailure.CONFIGURATION
        AttestationUnavailableException.Reason.DEVICE_NOT_TRUSTED -> RequestFailure.DEVICE_NOT_TRUSTED
        AttestationUnavailableException.Reason.PLAY_UNAVAILABLE,
        AttestationUnavailableException.Reason.TRANSIENT,
        -> RequestFailure.ATTESTATION_UNAVAILABLE
    }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
