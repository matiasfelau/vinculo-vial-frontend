package ar.felaufernandezbottaro.vinculovial.data.session

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * La clave de desbloqueo en el Android Keystore: RSA 2048, sólo para descifrar, con
 * autenticación biométrica fuerte en cada uso y atada al conjunto de huellas enroladas (si se
 * enrola una nueva, el sistema la invalida).
 *
 * La pública se reconstruye fuera del Keystore antes de cifrar: así cifrar no pasa por el
 * proveedor del Keystore, que exigiría la autenticación que esta clave pide sólo para descifrar.
 */
class KeystoreSessionKeyVault : SessionKeyVault {

    override fun createKey() {
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(KEY_SIZE_BITS)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(AUTH_ON_EVERY_USE_SECONDS, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(LEGACY_AUTH_ON_EVERY_USE)
        }

        try {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
                .apply { initialize(builder.build()) }
                .generateKeyPair()
        } catch (e: GeneralSecurityException) {
            throw CryptoOperationException("No se pudo crear la clave de desbloqueo", e)
        } catch (e: ProviderException) {
            throw CryptoOperationException("No se pudo crear la clave de desbloqueo", e)
        }
    }

    override fun wrap(plaintext: ByteArray): ByteArray = try {
        val stored = keyStore().getCertificate(KEY_ALIAS)?.publicKey
            ?: throw CryptoOperationException("No hay clave de desbloqueo para envolver")
        val publicKey = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
            .generatePublic(X509EncodedKeySpec(stored.encoded))
        SessionWrapCipher.newCipher().apply {
            init(Cipher.ENCRYPT_MODE, publicKey, SessionWrapCipher.oaepSpec())
        }.doFinal(plaintext)
    } catch (e: GeneralSecurityException) {
        throw CryptoOperationException("No se pudo envolver el refresh token", e)
    } catch (e: ProviderException) {
        throw CryptoOperationException("No se pudo envolver el refresh token", e)
    }

    override fun createUnlockCipher(): Cipher {
        val privateKey = try {
            keyStore().getKey(KEY_ALIAS, null) as? PrivateKey
        } catch (e: UnrecoverableKeyException) {
            throw SessionKeyInvalidatedException("La clave de desbloqueo ya no se puede recuperar", e)
        } catch (e: GeneralSecurityException) {
            throw CryptoOperationException("No se pudo leer la clave de desbloqueo", e)
        } ?: throw SessionKeyInvalidatedException("No hay clave de desbloqueo")

        return try {
            SessionWrapCipher.newCipher().apply {
                init(Cipher.DECRYPT_MODE, privateKey, SessionWrapCipher.oaepSpec())
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw SessionKeyInvalidatedException("Cambiaron las huellas enroladas", e)
        } catch (e: GeneralSecurityException) {
            throw CryptoOperationException("No se pudo preparar el descifrado", e)
        } catch (e: ProviderException) {
            throw CryptoOperationException("No se pudo preparar el descifrado", e)
        }
    }

    override fun deleteKey() {
        try {
            keyStore().deleteEntry(KEY_ALIAS)
        } catch (e: GeneralSecurityException) {
            throw CryptoOperationException("No se pudo borrar la clave de desbloqueo", e)
        }
    }

    private fun keyStore(): KeyStore = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } catch (e: IOException) {
        throw CryptoOperationException("No se pudo abrir el Android Keystore", e)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "vv_session_unlock"
        const val KEY_SIZE_BITS = 2048

        /** API 30 o más: 0 segundos de validez = autenticación en cada uso. */
        const val AUTH_ON_EVERY_USE_SECONDS = 0

        /** API 26 a 29: -1 = autenticación en cada uso, con el `CryptoObject` del prompt. */
        const val LEGACY_AUTH_ON_EVERY_USE = -1
    }
}
