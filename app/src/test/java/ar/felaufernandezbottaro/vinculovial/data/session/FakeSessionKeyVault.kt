package ar.felaufernandezbottaro.vinculovial.data.session

import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * [SessionKeyVault] en JVM: RSA 2048 real con el mismo esquema OAEP que el Keystore
 * ([SessionWrapCipher]), sin autenticación biométrica -- el prompt no existe en una prueba
 * unitaria. Permite simular lo que el Keystore real puede hacer: invalidar la clave (huellas
 * nuevas) o fallar al crear o envolver.
 *
 * Generar un par RSA es lento, así que todos los fakes comparten el mismo; lo que cada instancia
 * lleva por su cuenta es si "existe" la clave.
 */
class FakeSessionKeyVault : SessionKeyVault {

    /** Simula que se enroló una huella nueva: el `Cipher` de desbloqueo ya no se puede crear. */
    var invalidated = false
    var failOnCreate = false
    var failOnWrap = false

    var createCount = 0
        private set
    var deleteCount = 0
        private set

    var hasKey = false
        private set

    override fun createKey() {
        if (failOnCreate) throw CryptoOperationException("no se pudo crear la clave")
        createCount++
        hasKey = true
        invalidated = false
    }

    override fun wrap(plaintext: ByteArray): ByteArray {
        if (failOnWrap) throw CryptoOperationException("no se pudo envolver")
        check(hasKey) { "no hay clave" }
        // Igual que la implementación real: la pública se reconstruye fuera del proveedor.
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(sharedPair.public.encoded))
        return SessionWrapCipher.newCipher().apply {
            init(Cipher.ENCRYPT_MODE, publicKey, SessionWrapCipher.oaepSpec())
        }.doFinal(plaintext)
    }

    override fun createUnlockCipher(): Cipher {
        if (!hasKey || invalidated) throw SessionKeyInvalidatedException("clave invalidada")
        return SessionWrapCipher.newCipher().apply {
            init(Cipher.DECRYPT_MODE, sharedPair.private, SessionWrapCipher.oaepSpec())
        }
    }

    override fun deleteKey() {
        deleteCount++
        hasKey = false
    }

    private companion object {
        val sharedPair: KeyPair by lazy {
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        }
    }
}
