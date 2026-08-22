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
import java.security.GeneralSecurityException
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

    private val vehiclePrivateKeyset: KeysetHandle = KeysetHandle.generateNew(
        KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
    )

    private val vehiclePublicKeyBase64: String = Base64.getEncoder().encodeToString(
        (vehiclePrivateKeyset.publicKeysetHandle.primary.key as HpkePublicKey)
            .publicKeyBytes
            .toByteArray(),
    )

    private val encryptor = HpkeEncryptor()

    private val payload = """{"dni":"12345678","email":"usuario@ejemplo.com","password":"Password123!"}"""

    @Test
    fun `identity puede descifrar el mensaje que produce la app`() {
        val message = encryptor.encrypt(payload.toByteArray(), identityPublicKeyBase64)

        assertEquals(payload, String(decryptAsIdentity(message)))
    }

    @Test
    fun `la clave efimera mide 32 bytes`() {
        val message = encryptor.encrypt(payload.toByteArray(), identityPublicKeyBase64)

        assertEquals(32, Base64.getDecoder().decode(message.ephemeralPublicKey).size)
    }

    @Test
    fun `dos cifrados del mismo payload no se parecen`() {
        val primero = encryptor.encrypt(payload.toByteArray(), identityPublicKeyBase64)
        val segundo = encryptor.encrypt(payload.toByteArray(), identityPublicKeyBase64)

        assertNotEquals(primero.ephemeralPublicKey, segundo.ephemeralPublicKey)
        assertNotEquals(primero.ciphertext, segundo.ciphertext)
        assertEquals(payload, String(decryptAsIdentity(primero)))
        assertEquals(payload, String(decryptAsIdentity(segundo)))
    }

    @Test
    fun `el ciphertext no contiene el payload en claro`() {
        val message = encryptor.encrypt(payload.toByteArray(), identityPublicKeyBase64)

        val descifrable = Base64.getDecoder().decode(message.ciphertext)
        assertTrue(descifrable.isNotEmpty())
        assertTrue(!String(descifrable, Charsets.ISO_8859_1).contains("12345678"))
    }

    /**
     * El requerimiento central del cifrado diferenciado de US-04: cifrar contra una clave
     * pública distinta produce un mensaje que sólo la contraparte dueña de esa clave puede
     * abrir. Si `dni_enc` y `pat_enc` se cifraran con la misma clave por error, este test
     * fallaría -- Vehicle no podría seguir sin poder leer el DNI.
     */
    @Test
    fun `cifrar contra la clave de vehicle no lo puede abrir identity, y viceversa`() {
        val paraVehicle = encryptor.encrypt(payload.toByteArray(), vehiclePublicKeyBase64)
        val paraIdentity = encryptor.encrypt(payload.toByteArray(), identityPublicKeyBase64)

        assertThrows(GeneralSecurityException::class.java) { decryptAsIdentity(paraVehicle) }
        assertThrows(GeneralSecurityException::class.java) { decryptAsVehicle(paraIdentity) }

        // Confirma que cada uno sí puede abrir el mensaje cifrado para él.
        assertEquals(payload, String(decryptAsVehicle(paraVehicle)))
        assertEquals(payload, String(decryptAsIdentity(paraIdentity)))
    }

    @Test
    fun `clave sin configurar es un error de configuracion`() {
        val error = assertThrows(CryptoConfigurationException::class.java) {
            encryptor.encrypt(payload.toByteArray(), "")
        }

        assertTrue(error.message!!.contains("no está configurada"))
    }

    @Test
    fun `clave que no es base64 es un error de configuracion`() {
        assertThrows(CryptoConfigurationException::class.java) {
            encryptor.encrypt(payload.toByteArray(), "no-es-base64-!!")
        }
    }

    @Test
    fun `clave con longitud incorrecta es un error de configuracion`() {
        val corta = Base64.getEncoder().encodeToString(ByteArray(16))

        assertThrows(CryptoConfigurationException::class.java) {
            encryptor.encrypt(payload.toByteArray(), corta)
        }
    }

    @Test
    fun `toCryptogram concatena enc y ciphertext en un unico campo`() {
        val message = encryptor.encrypt(payload.toByteArray(), identityPublicKeyBase64)

        val cryptogram = Base64.getDecoder().decode(message.toCryptogram())
        val enc = Base64.getDecoder().decode(message.ephemeralPublicKey)
        val ciphertext = Base64.getDecoder().decode(message.ciphertext)

        assertEquals(enc.size + ciphertext.size, cryptogram.size)
        assertEquals(payload, String(decryptAsIdentity(message)))
        val decrypt = identityPrivateKeyset.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        assertEquals(payload, String(decrypt.decrypt(cryptogram, ByteArray(0))))
    }

    /** Reconstruye el mensaje HPKE tal como lo haría Identity y lo descifra. */
    private fun decryptAsIdentity(message: HpkeMessage): ByteArray = message.decryptAs(identityPrivateKeyset)

    /** Reconstruye el mensaje HPKE tal como lo haría Vehicle y lo descifra. */
    private fun decryptAsVehicle(message: HpkeMessage): ByteArray = message.decryptAs(vehiclePrivateKeyset)

    private fun HpkeMessage.decryptAs(privateKeyset: KeysetHandle): ByteArray {
        val enc = Base64.getDecoder().decode(ephemeralPublicKey)
        val ciphertext = Base64.getDecoder().decode(this.ciphertext)
        val decrypt = privateKeyset.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        return decrypt.decrypt(enc + ciphertext, ByteArray(0))
    }
}
