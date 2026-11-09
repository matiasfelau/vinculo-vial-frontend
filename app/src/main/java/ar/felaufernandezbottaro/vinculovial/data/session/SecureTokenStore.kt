package ar.felaufernandezbottaro.vinculovial.data.session

import android.content.SharedPreferences
import android.util.Log
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64
import javax.crypto.Cipher
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
 * Única puerta de acceso a la sesión (US-12). Nunca se loguea ningún token.
 *
 * - **Access token:** vive sólo en memoria. Nunca se persiste.
 * - **Refresh token:** en memoria mientras la sesión está [SessionState.UNLOCKED]. Sólo si el
 *   usuario activó la biometría se guarda además un blob: el refresh token envuelto con la clave
 *   de [SessionKeyVault] (RSA-OAEP), en base64, bajo [KEY_WRAPPED_REFRESH]. Es lo único que la
 *   app persiste de la sesión. Sin biometría activada no hay nada en disco: matar el proceso
 *   equivale a perder la sesión.
 * - **Estado:** [sessionState] pasa a [SessionState.LOCKED] cuando hay blob y no hay nada en
 *   memoria -- por ejemplo, al arrancar el proceso -- y a [SessionState.NONE] cuando no hay
 *   nada.
 *
 * Las operaciones que tocan la red (rotar, desbloquear, cerrar sesión) las coordina
 * `SessionManager` bajo su `Mutex`; esta clase sólo guarda el estado y mantiene el blob al día.
 */
@Singleton
class SecureTokenStore @Inject constructor(
    private val prefs: SharedPreferences,
    private val vault: SessionKeyVault,
) {

    @Volatile
    private var tokens: SessionTokens? = null

    private val _sessionState = MutableStateFlow(initialState())

    /** Refleja en tiempo real en qué mundo está la app: es lo que hace reaccionar al nav host. */
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(hasWrappedRefresh())

    /** Hay un blob guardado: el desbloqueo con biometría está activado. */
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _biometricOfferPending = MutableStateFlow(false)

    /** Se acaba de entrar con la contraseña: corresponde ofrecer activar la biometría, una vez. */
    val biometricOfferPending: StateFlow<Boolean> = _biometricOfferPending.asStateFlow()

    /** Los tokens en memoria, o `null` si la sesión no está desbloqueada. */
    fun read(): SessionTokens? = tokens

    /** Hay tokens en memoria. No decodifica el JWT ni mira su expiración. */
    fun hasSession(): Boolean = tokens != null

    /**
     * Login con contraseña. Cualquier blob y clave anteriores se borran acá, recién ahora que el
     * login salió bien ("Usar contraseña" desde el desbloqueo no los toca hasta este punto).
     */
    @Synchronized
    fun startSession(newTokens: SessionTokens) {
        discardBiometric()
        tokens = newTokens
        _biometricOfferPending.value = true
        _sessionState.value = SessionState.UNLOCKED
    }

    /**
     * Rotación del refresh token. Con la biometría activa, el refresh nuevo se vuelve a envolver
     * con la pública, sin prompt. Quien llama lo hace bajo el mismo lock que la rotación.
     */
    @Synchronized
    fun rotate(newTokens: SessionTokens) {
        tokens = newTokens
        rewrapOrDisable(newTokens.refreshToken)
    }

    /** Desbloqueo exitoso: el refresh recién rotado reemplaza al blob y la sesión pasa a UNLOCKED. */
    @Synchronized
    fun unlockWith(newTokens: SessionTokens) {
        tokens = newTokens
        rewrapOrDisable(newTokens.refreshToken)
        _sessionState.value = SessionState.UNLOCKED
    }

    /**
     * Crea el par de claves y envuelve el refresh actual. `false` si no hay sesión desbloqueada
     * o si el dispositivo no pudo crear la clave (queda todo como antes, sin biometría).
     */
    @Synchronized
    fun enableBiometric(): Boolean {
        val current = tokens ?: return false
        return try {
            vault.createKey()
            persistWrapped(current.refreshToken)
            _biometricEnabled.value = true
            _biometricOfferPending.value = false
            true
        } catch (e: CryptoOperationException) {
            Log.w(TAG, "No se pudo activar la biometría: ${e.javaClass.simpleName}")
            discardBiometric()
            // El ofrecimiento es una vez por login: un intento fallido también lo consume.
            _biometricOfferPending.value = false
            false
        }
    }

    /** Desactivar desde Settings: borra el blob y la clave. La sesión sigue desbloqueada. */
    @Synchronized
    fun disableBiometric() {
        discardBiometric()
        _biometricOfferPending.value = false
    }

    /** El usuario rechazó el ofrecimiento: no se vuelve a ofrecer hasta el próximo login. */
    fun dismissBiometricOffer() {
        _biometricOfferPending.value = false
    }

    /**
     * Bloqueo: borra access y refresh de la memoria. Con biometría activada queda LOCKED; sin
     * ella no queda nada y el estado es NONE (el cierre en el servidor lo hace `SessionManager`).
     */
    @Synchronized
    fun lock() {
        tokens = null
        _biometricOfferPending.value = false
        _sessionState.value = if (_biometricEnabled.value) SessionState.LOCKED else SessionState.NONE
    }

    /** Sin sesión: memoria, blob y clave. La llaman el logout, la baja de cuenta y el 401 del refresh. */
    @Synchronized
    fun clear() {
        tokens = null
        _biometricOfferPending.value = false
        discardBiometric()
        _sessionState.value = SessionState.NONE
    }

    /** El blob tal como se guardó, o `null` si no hay (o quedó ilegible). */
    fun readWrappedRefresh(): ByteArray? {
        val encoded = prefs.getString(KEY_WRAPPED_REFRESH, null) ?: return null
        return try {
            DECODER.decode(encoded)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "El blob de sesión está corrupto: ${e.javaClass.simpleName}")
            null
        }
    }

    /** El `Cipher` para el prompt biométrico. Ver [SessionKeyVault.createUnlockCipher]. */
    fun createUnlockCipher(): Cipher = vault.createUnlockCipher()

    private fun initialState(): SessionState =
        if (hasWrappedRefresh()) SessionState.LOCKED else SessionState.NONE

    private fun hasWrappedRefresh(): Boolean = prefs.contains(KEY_WRAPPED_REFRESH)

    private fun persistWrapped(refreshToken: String) {
        val plaintext = refreshToken.toByteArray(Charsets.UTF_8)
        try {
            val wrapped = vault.wrap(plaintext)
            val saved = prefs.edit().putString(KEY_WRAPPED_REFRESH, ENCODER.encodeToString(wrapped)).commit()
            if (!saved) throw CryptoOperationException("No se pudo guardar el blob de sesión")
        } finally {
            plaintext.fill(0)
        }
    }

    /**
     * Si no se puede volver a envolver, el blob viejo quedaría con un refresh ya rotado: usarlo
     * más tarde sería un reúso, que el servidor castiga revocando todas las sesiones. Por eso
     * se desactiva la biometría -- el usuario sigue en sesión y la vuelve a activar cuando
     * quiera -- en vez de dejar el blob desactualizado.
     */
    private fun rewrapOrDisable(refreshToken: String) {
        if (!_biometricEnabled.value) return
        try {
            persistWrapped(refreshToken)
        } catch (e: CryptoOperationException) {
            Log.w(TAG, "No se pudo volver a envolver el refresh: ${e.javaClass.simpleName}")
            discardBiometric()
        }
    }

    /** Primero el blob y después la clave: si el proceso muere entre los dos, sobra una clave huérfana, inofensiva. */
    private fun discardBiometric() {
        if (!prefs.edit().clear().commit()) Log.w(TAG, "No se pudo borrar el blob de sesión")
        _biometricEnabled.value = false
        try {
            vault.deleteKey()
        } catch (e: CryptoOperationException) {
            Log.w(TAG, "No se pudo borrar la clave de desbloqueo: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "SecureTokenStore"
        const val KEY_WRAPPED_REFRESH = "session_refresh_wrapped"
        val ENCODER: Base64.Encoder = Base64.getEncoder()
        val DECODER: Base64.Decoder = Base64.getDecoder()
    }
}
