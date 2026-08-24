package ar.felaufernandezbottaro.vinculovial.data.session

import android.content.SharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import com.google.crypto.tink.Aead
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.GeneralSecurityException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Par de tokens de sesión, tal como los devuelve `/signin`.
 */
data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
) {
    override fun toString(): String = "SessionTokens(***)"
}

/**
 * Única puerta de acceso a los tokens de sesión. Nunca en `SharedPreferences` plano, nunca
 * en memoria estática, nunca logueado.
 *
 * Cada token se cifra con AES-256-GCM (Tink) contra una clave que nunca sale del Android
 * Keystore ([di.SessionModule] arma ese `Aead`) — lo que hay en [prefs] no es más que dos
 * strings opacos; lo que los hace seguros es que la clave no puede operar fuera de este
 * dispositivo.
 *
 * `androidx.security:security-crypto` (EncryptedSharedPreferences) es la combinación que
 * originalmente inspiró esta clase, pero Google la deprecó en favor del uso directo de
 * Keystore. Por eso acá se arma el cifrado directo con Tink — que el proyecto ya usa para
 * HPKE en `HpkeEncryptor` y que sigue activamente mantenido — en vez de sumar una
 * dependencia nueva que ya nace obsoleta.
 */
@Singleton
class SecureTokenStore @Inject constructor(
    private val prefs: SharedPreferences,
    private val aead: Aead,
) {

    /**
     * Refleja en tiempo real si hay una sesión guardada. Es lo que le permite a la UI
     * enterarse de que la sesión se cayó en segundo plano -- por ejemplo, un RTR fallido
     * disparado por una pantalla que no es la que está en pantalla -- y redirigir a Login sin
     * que nadie tenga que consultarlo activamente.
     */
    private val _sessionState = MutableStateFlow(hasSession())
    val sessionState: StateFlow<Boolean> = _sessionState.asStateFlow()

    /**
     * Guarda el par en una única transacción de [SharedPreferences]: nunca queda uno de los
     * dos tokens guardado sin el otro.
     */
    fun save(tokens: SessionTokens) {
        try {
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, encrypt(KEY_ACCESS_TOKEN, tokens.accessToken))
                .putString(KEY_REFRESH_TOKEN, encrypt(KEY_REFRESH_TOKEN, tokens.refreshToken))
                .apply()
            _sessionState.value = true
        } catch (e: GeneralSecurityException) {
            throw CryptoOperationException("No se pudo cifrar el par de tokens de sesión", e)
        }
    }

    /**
     * `null` si no hay sesión guardada o si el par quedó corrupto/incompleto (por ejemplo,
     * el dispositivo perdió la clave de Keystore tras un restore a otro equipo): un fallo de
     * lectura se trata igual que "no hay sesión", nunca como una excepción que tumbe la
     * pantalla que la consulta.
     */
    fun read(): SessionTokens? {
        val rawAccess = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val rawRefresh = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null

        return try {
            SessionTokens(
                accessToken = decrypt(KEY_ACCESS_TOKEN, rawAccess),
                refreshToken = decrypt(KEY_REFRESH_TOKEN, rawRefresh),
            )
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            // Base64 corrupto: mismo tratamiento que un fallo de descifrado.
            null
        }
    }

    /** Existencia, no validez: no decodifica el JWT ni mira su expiración. */
    fun hasSession(): Boolean = read() != null

    fun clear() {
        prefs.edit().clear().apply()
        _sessionState.value = false
    }

    /** El nombre de la clave como AAD liga cada ciphertext a su lugar: uno no sirve para el otro. */
    private fun encrypt(key: String, value: String): String =
        ENCODER.encodeToString(aead.encrypt(value.toByteArray(Charsets.UTF_8), key.toByteArray(Charsets.UTF_8)))

    private fun decrypt(key: String, value: String): String =
        String(aead.decrypt(DECODER.decode(value), key.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        val ENCODER: Base64.Encoder = Base64.getEncoder()
        val DECODER: Base64.Decoder = Base64.getDecoder()
    }
}
