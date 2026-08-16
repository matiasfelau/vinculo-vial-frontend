package ar.felaufernandezbottaro.vinculovial.data.crypto

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.Base64

/**
 * El round-trip usa una clave privada generada acá, jugando el papel de
 * `vinculo-vial-identity`: si Identity puede descifrar lo que la app produce,
 * el contrato del payload externo está bien implementado.
 */
class HpkeEncryptorTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun registerTink() {
            HybridConfig.register()
        }
    }

    private val identityPrivateKeyset: KeysetHandle = KeysetHandle.generateNew(
        KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
    )

    private val identityPublicKeyBase64: String = Base64.getEncoder().encodeToString(
        (identityPrivateKeyset.publicKeysetHandle.primary.key as HpkePublicKey)
            .publicKeyBytes
            .toByteArray(),
    )

    private val payload = """{"dni":"12345678","email":"usuario@ejemplo.com","password":"Password123!"}"""

    @Test
    fun `identity puede descifrar el mensaje que produce la app`() {
        val message = HpkeEncryptor(identityPublicKeyBase64).encrypt(payload.toByteArray())

        assertEquals(payload, String(decryptAsIdentity(message)))
    }

    @Test
    fun `la clave efimera mide 32 bytes`() {
        val message = HpkeEncryptor(identityPublicKeyBase64).encrypt(payload.toByteArray())

        assertEquals(32, Base64.getDecoder().decode(message.ephemeralPublicKey).size)
    }

    @Test
    fun `dos cifrados del mismo payload no se parecen`() {
        val encryptor = HpkeEncryptor(identityPublicKeyBase64)

        val primero = encryptor.encrypt(payload.toByteArray())
        val segundo = encryptor.encrypt(payload.toByteArray())

        assertNotEquals(primero.ephemeralPublicKey, segundo.ephemeralPublicKey)
        assertNotEquals(primero.ciphertext, segundo.ciphertext)
        assertEquals(payload, String(decryptAsIdentity(primero)))
        assertEquals(payload, String(decryptAsIdentity(segundo)))
    }

    @Test
    fun `el ciphertext no contiene el payload en claro`() {
        val message = HpkeEncryptor(identityPublicKeyBase64).encrypt(payload.toByteArray())

        val descifrable = Base64.getDecoder().decode(message.ciphertext)
        assertTrue(descifrable.isNotEmpty())
        assertTrue(!String(descifrable, Charsets.ISO_8859_1).contains("12345678"))
    }

    @Test
    fun `clave sin configurar es un error de configuracion`() {
        val error = assertThrows(CryptoConfigurationException::class.java) {
            HpkeEncryptor("").encrypt(payload.toByteArray())
        }

        assertTrue(error.message!!.contains("IDENTITY_HPKE_PUBLIC_KEY"))
    }

    @Test
    fun `clave que no es base64 es un error de configuracion`() {
        assertThrows(CryptoConfigurationException::class.java) {
            HpkeEncryptor("no-es-base64-!!").encrypt(payload.toByteArray())
        }
    }

    @Test
    fun `clave con longitud incorrecta es un error de configuracion`() {
        val corta = Base64.getEncoder().encodeToString(ByteArray(16))

        assertThrows(CryptoConfigurationException::class.java) {
            HpkeEncryptor(corta).encrypt(payload.toByteArray())
        }
    }

    /** Reconstruye el mensaje HPKE tal como lo haría Identity y lo descifra. */
    private fun decryptAsIdentity(message: HpkeMessage): ByteArray {
        val enc = Base64.getDecoder().decode(message.ephemeralPublicKey)
        val ciphertext = Base64.getDecoder().decode(message.ciphertext)
        val decrypt = identityPrivateKeyset.getPrimitive(
            RegistryConfiguration.get(),
            HybridDecrypt::class.java,
        )
        return decrypt.decrypt(enc + ciphertext, ByteArray(0))
    }
}
