package ar.felaufernandezbottaro.vinculovial.data.session

import java.security.GeneralSecurityException
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * La clave que envuelve el refresh token mientras la sesión está bloqueada (US-12). Detrás de una
 * interfaz porque el Android Keystore sólo existe en un dispositivo: la implementación real es
 * [KeystoreSessionKeyVault] y las pruebas usan un fake que cumple el mismo contrato.
 *
 * Es un par RSA, no una clave simétrica, a propósito: mientras la app está desbloqueada el
 * refresh token rota cada pocos minutos, y con una clave simétrica que exige autenticación cada
 * rotación pediría la huella para volver a cifrar. Con RSA se cifra con la pública, sin prompt,
 * y sólo descifrar exige la huella. No se simplifica a AES.
 */
interface SessionKeyVault {

    /** Genera el par de claves (reemplazando uno anterior) con la privada atada a la biometría. */
    fun createKey()

    /** Cifra con la pública, sin autenticación del usuario. */
    fun wrap(plaintext: ByteArray): ByteArray

    /**
     * Un [Cipher] de descifrado con la privada, listo para `BiometricPrompt.CryptoObject`.
     *
     * @throws SessionKeyInvalidatedException si la clave ya no existe o el sistema la invalidó
     * (típicamente, porque se enroló una huella nueva).
     */
    fun createUnlockCipher(): Cipher

    /** Borra el par de claves. Nada envuelto con él vuelve a poder abrirse. */
    fun deleteKey()
}

/** La clave de desbloqueo ya no sirve: hay que volver a entrar con la contraseña. */
class SessionKeyInvalidatedException(message: String, cause: Throwable? = null) :
    GeneralSecurityException(message, cause)

/**
 * El esquema de cifrado del blob, compartido por la implementación real y por el fake de las
 * pruebas para que no puedan divergir. El mismo spec se usa al cifrar y al descifrar: si no
 * coincide, el Keystore falla.
 */
object SessionWrapCipher {

    const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

    private const val DIGEST_ALGORITHM = "SHA-256"
    private const val MGF_ALGORITHM = "MGF1"

    fun newCipher(): Cipher = Cipher.getInstance(TRANSFORMATION)

    fun oaepSpec(): OAEPParameterSpec =
        OAEPParameterSpec(DIGEST_ALGORITHM, MGF_ALGORITHM, MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
}
