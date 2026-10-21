package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Base64

/** La patente se cifra directa contra la clave de **Vehicle**, nunca la de IA, con `info` vacío. */
class VehiclePlateEncryptorTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun registerTink() {
            HybridConfig.register()
            AeadConfig.register()
        }
    }

    private val vehiclePrivateKeyset: KeysetHandle = KeysetHandle.generateNew(
        KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
    )
    private val vehiclePublicKeyBase64 = vehiclePrivateKeyset.publicKeyBase64()

    /** Par de IA: jamás se le pasa al cifrador, sólo sirve para comprobar que no abre el pat_enc. */
    private val iaPrivateKeyset: KeysetHandle = KeysetHandle.generateNew(
        KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
    )

    private val patente = "GUQ856"

    private fun encryptor(vehiclePublicKey: String = vehiclePublicKeyBase64) =
        VehiclePlateEncryptor(HpkeEncryptor(), vehiclePublicKey)

    @Test
    fun `el pat_enc solo lo puede abrir vehicle y contiene la patente en claro`() {
        val patEnc = encryptor().encrypt(patente)

        assertNotNull(patEnc)
        assertEquals(patente, String(decrypt(vehiclePrivateKeyset, patEnc!!)))
    }

    @Test
    fun `el pat_enc no lo puede abrir la clave de IA`() {
        val patEnc = encryptor().encrypt(patente)!!

        assertThrows(GeneralSecurityException::class.java) { decrypt(iaPrivateKeyset, patEnc) }
    }

    @Test
    fun `clave de vehicle mal configurada devuelve null`() {
        assertNull(encryptor(vehiclePublicKey = "").encrypt(patente))
    }

    private fun KeysetHandle.publicKeyBase64(): String = Base64.getEncoder().encodeToString(
        (publicKeysetHandle.primary.key as HpkePublicKey).publicKeyBytes.toByteArray(),
    )

    private fun decrypt(privateKeyset: KeysetHandle, cryptogram: String): ByteArray {
        val sealed = Base64.getDecoder().decode(cryptogram)
        val decrypt = privateKeyset.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        return decrypt.decrypt(sealed, ByteArray(0))
    }
}
