package ar.felaufernandezbottaro.vinculovial.data.crypto

import java.io.IOException
import java.security.MessageDigest

/**
 * Produce el valor del header `X-App-Attestation`.
 *
 * Hay dos implementaciones intercambiables por configuración:
 * - [MockAttestationProvider]: JWT firmado con la PSK compartida con el Gateway.
 * - [PlayIntegrityAttestationProvider]: veredicto real de Google Play Integrity.
 *
 * Ambas ligan el token al cuerpo de la request mediante un hash, para que un token
 * capturado no sirva para firmar otro registro.
 */
interface AttestationProvider {

    /**
     * @param requestBody bytes exactos del cuerpo que se va a enviar.
     * @throws AttestationUnavailableException si no se puede obtener un veredicto.
     */
    suspend fun tokenFor(requestBody: ByteArray): String
}

/**
 * Modo de attestation elegido en tiempo de build.
 *
 * El Gateway valida de forma distinta según el modo, así que ambos repos tienen que
 * estar configurados igual.
 */
enum class AttestationMode {
    /** Sólo desarrollo: JWT HMAC con `APP_ATTESTATION_PSK`. Hay que pedirlo explícitamente. */
    MOCK,

    /** Default: token de Google, que el Gateway decodifica contra la Play Integrity API. */
    PLAY_INTEGRITY,

    ;

    companion object {
        /**
         * Falla cerrado: cualquier valor que no sea exactamente `mock` cae en la
         * verificación real. Un typo en la configuración no debe degradar la seguridad.
         */
        fun from(raw: String): AttestationMode = when (raw.trim().lowercase()) {
            "mock" -> MOCK
            else -> PLAY_INTEGRITY
        }
    }
}

/**
 * No se pudo obtener un veredicto de integridad en el dispositivo.
 *
 * Extiende [IOException] a propósito: se lanza desde un interceptor de OkHttp, y las
 * excepciones que no son de IO rompen la llamada de forma menos predecible.
 */
class AttestationUnavailableException(
    message: String,
    val reason: Reason,
    cause: Throwable? = null,
) : IOException(message, cause) {

    enum class Reason {
        /** Falta configuración de build (PSK, número de proyecto). */
        MISCONFIGURED,

        /** No hay Play Store o Play Services utilizable en el dispositivo. */
        PLAY_UNAVAILABLE,

        /** Google no reconoce la app o el dispositivo. */
        DEVICE_NOT_TRUSTED,

        /** Fallo transitorio: red, cuota, servicio caído. */
        TRANSIENT,
    }
}

/**
 * Liga el token al cuerpo de la request.
 *
 * Formato: SHA-256 del cuerpo, en hexadecimal minúscula (64 caracteres) — así lo espera
 * `vinculo-vial-gateway`, bien dentro del límite de 500 bytes que acepta `requestHash`
 * en Play Integrity. El mock usa el mismo valor como claim para que ambos modos se
 * validen igual del lado del Gateway.
 */
internal fun requestHashOf(requestBody: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(requestBody).toHex()

private val HEX_DIGITS = "0123456789abcdef".toCharArray()

private fun ByteArray.toHex(): String {
    val chars = CharArray(size * 2)
    forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xFF
        chars[index * 2] = HEX_DIGITS[value ushr 4]
        chars[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
    }
    return String(chars)
}
