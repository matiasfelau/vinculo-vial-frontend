package ar.felaufernandezbottaro.vinculovial.data.evidence

import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoConfigurationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.edge.SecureImageBuffer
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Base64

/**
 * La app es la única que cifra las fotos y sólo IA las puede abrir: el roundtrip usa un par de
 * claves de test jugando el papel de `vinculo-vial-ia`.
 */
class EvidenceEncryptorTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun registerTink() {
            HybridConfig.register()
        }

        val BASE64_ALPHABET: Set<Byte> =
            (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('+', '/', '=')).map { it.code.toByte() }.toSet()
    }

    private val iaKeyset = newKeyset()
    private val vehicleKeyset = newKeyset()

    private val attemptId = "3f2b8c1e-0000-4000-8000-000000000001"
    private val jpeg = ByteArray(2_048) { (it % 251).toByte() }

    private fun encryptor(iaPublicKey: String = iaKeyset.publicKeyBase64()) =
        EvidenceEncryptor(HpkeEncryptor(), iaPublicKey)

    @Test
    fun `roundtrip con el par de IA y el attempt_id como info`() {
        val sealed = encryptor().encrypt(SecureImageBuffer.copyOf(jpeg), attemptId)

        assertArrayEquals(jpeg, decrypt(iaKeyset, sealed, attemptId))
    }

    @Test
    fun `con otro attempt_id como info no se puede abrir`() {
        val sealed = encryptor().encrypt(SecureImageBuffer.copyOf(jpeg), attemptId)

        assertThrows(GeneralSecurityException::class.java) { decrypt(iaKeyset, sealed, "otro-attempt") }
    }

    @Test
    fun `la clave de Vehicle no puede abrir la foto`() {
        val sealed = encryptor().encrypt(SecureImageBuffer.copyOf(jpeg), attemptId)

        assertThrows(GeneralSecurityException::class.java) { decrypt(vehicleKeyset, sealed, attemptId) }
    }

    @Test
    fun `cifra contra la clave que recibe y no contra otra`() {
        val sealed = encryptor(iaPublicKey = vehicleKeyset.publicKeyBase64())
            .encrypt(SecureImageBuffer.copyOf(jpeg), attemptId)

        assertThrows(GeneralSecurityException::class.java) { decrypt(iaKeyset, sealed, attemptId) }
        assertArrayEquals(jpeg, decrypt(vehicleKeyset, sealed, attemptId))
    }

    @Test
    fun `devuelve bytes crudos enc y ct, no base64`() {
        val sealed = encryptor().encrypt(SecureImageBuffer.copyOf(jpeg), attemptId)

        // enc (32) + ciphertext + tag AES-GCM (16).
        assertEquals(32 + jpeg.size + 16, sealed.size)
        assertFalse(sealed.all { it in BASE64_ALPHABET })
    }

    @Test
    fun `dos cifrados de la misma foto no se parecen`() {
        val photo = SecureImageBuffer.copyOf(jpeg)

        val first = encryptor().encrypt(photo, attemptId)
        val second = encryptor().encrypt(photo, attemptId)

        assertFalse(first.contentEquals(second))
        assertNotEquals(first.toList().take(32), second.toList().take(32))
    }

    @Test
    fun `no libera el buffer en claro, hace falta para volver a cifrar con otro intento`() {
        val photo = SecureImageBuffer.copyOf(jpeg)

        encryptor().encrypt(photo, attemptId)

        assertFalse(photo.isCleared)
        assertArrayEquals(jpeg, photo.read { it.copyOf() })
    }

    @Test
    fun `sin clave de IA configurada falla con error de configuracion`() {
        assertThrows(CryptoConfigurationException::class.java) {
            encryptor(iaPublicKey = "").encrypt(SecureImageBuffer.copyOf(jpeg), attemptId)
        }
    }

    private fun newKeyset(): KeysetHandle =
        KeysetHandle.generateNew(KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"))

    private fun KeysetHandle.publicKeyBase64(): String = Base64.getEncoder().encodeToString(
        (publicKeysetHandle.primary.key as HpkePublicKey).publicKeyBytes.toByteArray(),
    )

    private fun decrypt(keyset: KeysetHandle, sealed: ByteArray, info: String): ByteArray =
        keyset.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
            .decrypt(sealed, info.toByteArray(Charsets.UTF_8))
}
