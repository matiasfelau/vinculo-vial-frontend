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
import ar.felaufernandezbottaro.vinculovial.data.signup.dto.SignUpErrorDetailDto
import ar.felaufernandezbottaro.vinculovial.data.signup.dto.SignUpErrorResponseDto
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
    private val errorResponseAdapter: JsonAdapter<SignUpErrorResponseDto> =
        moshi.adapter(SignUpErrorResponseDto::class.java)

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
            when {
                response.isSuccessful -> SignUpResult.Registered
                // Sólo el 400 tiene un cuerpo con contrato documentado (code + details[]);
                // 403/409/5xx se mapean sólo por código de estado, como siempre.
                response.code() == HTTP_BAD_REQUEST -> SignUpResult.Failed(handleBadRequest(response))
                else -> SignUpResult.Failed(response.code().toSignUpError())
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
     * Contrato real del 400, confirmado por el equipo de backend:
     * ```json
     * { "code": "datos_invalidos", "details": [ { "field": "sexo", "code": "formato_invalido", "message": "..." } ] }
     * { "code": "identidad_no_verificada" }
     * ```
     * `details[].code` es el valor estable (`requerido` / `formato_invalido` /
     * `longitud_invalida` / `password_debil` / `password_predecible`); `details[].message`
     * es texto para mostrar, pensado para cambiar de redacción — nunca se matchea contra él.
     * `identidad_no_verificada` (RENAPER no confirmó la selfie) es un 400 aparte, sin
     * `details`, deliberadamente genérico.
     */
    private fun handleBadRequest(response: Response<Unit>): SignUpError {
        val rawBody = try {
            response.errorBody()?.string()
        } catch (e: IOException) {
            null
        }
        val parsed = rawBody?.let { parseErrorBody(it) }
        logRejection(rawBody, parsed)

        return parsed?.toSignUpError() ?: SignUpError.InvalidData
    }

    private fun parseErrorBody(rawBody: String): SignUpErrorResponseDto? =
        try {
            errorResponseAdapter.fromJson(rawBody)
        } catch (e: Exception) {
            // JSON inesperado: se degrada a InvalidData en vez de propagar la excepción.
            // Un cuerpo de error mal formado no debería tumbar el manejo del error en sí.
            null
        }

    private fun SignUpErrorResponseDto.toSignUpError(): SignUpError = when (code) {
        CODE_IDENTITY_NOT_VERIFIED -> SignUpError.IdentityNotVerified
        CODE_INVALID_DATA -> details.orEmpty().toSignUpError()
        // Código de nivel superior no documentado todavía: no se inventa un mapeo específico.
        else -> SignUpError.InvalidData
    }

    /**
     * `dni`/`tramite`/`sexo`/`selfie` no son campos de un formulario — vienen del documento
     * o de la selfie, y la única corrección posible es repetir esa captura. Si además hay
     * un campo de formulario con la contraseña señalada por débil/predecible, ese detalle
     * más específico gana sobre el genérico — pero sólo si **ningún** campo de captura
     * también falló, porque arreglar la contraseña no sirve de nada si la captura igual se
     * va a rechazar de nuevo.
     */
    private fun List<SignUpErrorDetailDto>.toSignUpError(): SignUpError = when {
        isEmpty() -> SignUpError.InvalidData
        any { it.field !in FORM_FIELDS } -> SignUpError.DocumentDataRejected
        any { it.code in PASSWORD_REJECTION_CODES } -> SignUpError.PasswordRejected
        else -> SignUpError.InvalidData
    }

    /**
     * Diagnóstico de sólo debug: sin ver qué campo rechazó el backend no hay forma de saber
     * si el problema es el formato del payload o algo del lado del servidor.
     *
     * **Gateado a `BuildConfig.DEBUG`, nunca en release** — misma disciplina que el resto de
     * los logs de diagnóstico del proyecto (`TfLiteFaceEmbedder`, `CameraCapture`, `BIOMETRY`).
     * Cuando el cuerpo matchea el contrato documentado, sólo se loguean `field` y `code` —
     * los dos son vocabulario cerrado y acotado (6 campos, 5 códigos posibles), así que no
     * hay forma de que terminen filtrando un dato real. **Nunca se loguea `details[].message`
     * ni el cuerpo crudo en este caso** — el texto para mostrar es libre, y ahí sí podría
     * llegar a describir el valor rechazado.
     *
     * Si el cuerpo NO matchea el contrato (parseo falló), ahí sí se loguea crudo, porque no
     * hay forma de inspeccionarlo con seguridad — es la única rama de esta función con el
     * riesgo que tenía el logging original: Logcat no es un canal seguro, sobrevive en
     * reportes de bug y queda accesible por ADB. Debería ser un caso raro (el contrato ya
     * está confirmado); si aparece seguido, es señal de que el backend se desvió de lo
     * documentado.
     */
    private fun logRejection(rawBody: String?, parsed: SignUpErrorResponseDto?) {
        if (!BuildConfig.DEBUG) return

        val summary = when {
            parsed != null -> {
                val details = parsed.details.orEmpty().joinToString { "${it.field}:${it.code}" }
                "code=${parsed.code}" + if (details.isNotEmpty()) ", details=[$details]" else ""
            }
            rawBody != null -> "no matchea el contrato documentado, cuerpo crudo: $rawBody"
            else -> "sin cuerpo"
        }

        Log.e(TAG, "El backend rechazó el registro con 400 ($summary)")
    }

    // 400 nunca llega acá: se maneja aparte en handleBadRequest(), con su propio contrato.
    private fun Int.toSignUpError(): SignUpError = when (this) {
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

        const val CODE_INVALID_DATA = "datos_invalidos"
        const val CODE_IDENTITY_NOT_VERIFIED = "identidad_no_verificada"

        /** Los únicos dos campos que el usuario puede corregir volviendo al formulario. */
        val FORM_FIELDS = setOf("email", "password")

        val PASSWORD_REJECTION_CODES = setOf("password_debil", "password_predecible")
    }
}
