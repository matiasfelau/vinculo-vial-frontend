package ar.felaufernandezbottaro.vinculovial.data.signup

import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import ar.felaufernandezbottaro.vinculovial.BuildConfig
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoConfigurationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeMessage
import ar.felaufernandezbottaro.vinculovial.data.remote.AuthApiService
import ar.felaufernandezbottaro.vinculovial.data.signup.dto.SignUpPayloadDto
import ar.felaufernandezbottaro.vinculovial.data.signup.dto.SignUpRequestDto
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.domain.signup.SignUpError
import ar.felaufernandezbottaro.vinculovial.domain.signup.SignUpRepository
import ar.felaufernandezbottaro.vinculovial.domain.signup.SignUpResult
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.Credentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Buffer
import retrofit2.Response
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
                logRejection(response)
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
                    procedureNumber = credentials.procedureNumber,
                    sex = credentials.sex,
                    email = credentials.email,
                    password = credentials.password,
                    selfie = credentials.selfieBase64,
                ),
            )
            plaintext = buffer.readByteArray()
            encryptor.encrypt(plaintext)
        } finally {
            plaintext.fill(0)
            buffer.clear()
        }
    }

    /**
     * Diagnóstico de sólo debug para probar el flujo de punta a punta: el backend puede
     * describir en el cuerpo del 400 qué campo rechazó, y sin verlo no hay forma de saber
     * si el problema es el formato del payload o algo del lado del servidor.
     *
     * **Gateado a `BuildConfig.DEBUG`, nunca en release** — misma disciplina que el resto de
     * los logs de diagnóstico del proyecto (`TfLiteFaceEmbedder`, `CameraCapture`, `BIOMETRY`).
     * Pero acá hay un riesgo real que esos otros logs no tienen: si el backend, al describir
     * el campo inválido, **ecoa el valor recibido** (`"sexo recibido: 'Masculino'"` en vez de
     * `"sexo: formato inválido"`), este log filtra ese dato a Logcat pese al gate de debug —
     * Logcat no es un canal seguro, sobrevive en reportes de bug y queda accesible por ADB.
     * La regla del proyecto es "nunca loguear DNI, password, PSK" sin excepción de build type;
     * esto es una excepción deliberada y temporal para depurar la integración end-to-end, no
     * una decisión de arquitectura — hay que insistir con el equipo de backend en que la
     * respuesta describa el campo por **nombre o código**, nunca por valor.
     *
     * El mensaje que ve el usuario no cambia: sigue siendo el genérico de
     * `SignUpError.InvalidData`. Esto es sólo para quien está mirando el dispositivo conectado.
     */
    private fun logRejection(response: Response<Unit>) {
        if (!BuildConfig.DEBUG) return

        val body = try {
            response.errorBody()?.string()
        } catch (e: IOException) {
            "(no se pudo leer el cuerpo: ${e.message})"
        }

        Log.e(TAG, "El backend rechazó el registro (HTTP ${response.code()}): ${body ?: "(sin cuerpo)"}")
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
        const val TAG = "SignupError"
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_FORBIDDEN = 403
        const val HTTP_CONFLICT = 409
    }
}
