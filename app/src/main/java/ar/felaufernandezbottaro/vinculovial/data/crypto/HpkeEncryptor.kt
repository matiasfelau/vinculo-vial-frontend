package ar.felaufernandezbottaro.vinculovial.data.crypto

import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.util.Bytes
import java.security.GeneralSecurityException
import java.util.Base64
import javax.inject.Inject

/**
 * Mensaje HPKE listo para viajar por HTTP, tal como lo esperan `vinculo-vial-identity` y
 * `vinculo-vial-vehicle`.
 *
 * Es el encoding estándar de HPKE modo BASE partido en sus dos mitades:
 * `enc` (clave pública efímera del KEM) y el ciphertext AEAD.
 */
data class HpkeMessage(
    val ephemeralPublicKey: String,
    val ciphertext: String,
)

/**
 * Un único criptograma HPKE (`enc` + ciphertext concatenados y codificados juntos), tal
 * como lo espera el contrato de US-04: `{ "payload_enc": "<criptograma>" }`. A diferencia
 * de `HpkeMessage`, acá no viaja `enc`/ciphertext como dos campos separados por HTTP, sino
 * uno solo.
 */
fun HpkeMessage.toCryptogram(): String = Base64.getEncoder().encodeToString(
    Base64.getDecoder().decode(ephemeralPublicKey) + Base64.getDecoder().decode(ciphertext),
)

/**
 * Igual que [toCryptogram] pero en bytes crudos (`enc‖ct`, sin base64): es lo que viaja como
 * parte binaria `application/octet-stream` en el multipart de las fotos de evidencia.
 */
fun HpkeMessage.toCryptogramBytes(): ByteArray =
    Base64.getDecoder().decode(ephemeralPublicKey) + Base64.getDecoder().decode(ciphertext)

/**
 * Cifra un payload contra una Public Key X25519 HPKE.
 *
 * HPKE modo BASE — KEM DHKEM(X25519, HKDF-SHA256), KDF HKDF-SHA256, AEAD AES-256-GCM.
 * Cada llamada genera una clave efímera nueva: dos cifrados del mismo texto no se parecen.
 *
 * La clave pública de destino se recibe **por llamada**, nunca por constructor: la misma
 * instancia se reusa para cifrar contra la Public Key de Identity (`/signup`, `/signin`) y
 * contra la de Vehicle (`/vehicles`), y un default global haría trivial usar la que no
 * corresponde por error.
 */
class HpkeEncryptor @Inject constructor() {

    /**
     * @param plaintext payload interno serializado.
     * @param publicKeyBase64 clave pública cruda (32 bytes) del destinatario, en base64 estándar.
     * @param contextInfo info asociada de HPKE; vacía por contrato.
     */
    fun encrypt(plaintext: ByteArray, publicKeyBase64: String, contextInfo: ByteArray = EMPTY_CONTEXT): HpkeMessage {
        val hybridEncrypt = buildEncrypt(publicKeyBase64)

        val sealed = try {
            hybridEncrypt.encrypt(plaintext, contextInfo)
        } catch (e: GeneralSecurityException) {
            throw CryptoOperationException("No se pudo cifrar el payload", e)
        }

        if (sealed.size <= X25519_ENC_LENGTH) {
            throw CryptoOperationException("Salida HPKE más corta que la clave efímera")
        }

        return HpkeMessage(
            ephemeralPublicKey = ENCODER.encodeToString(sealed.copyOfRange(0, X25519_ENC_LENGTH)),
            ciphertext = ENCODER.encodeToString(sealed.copyOfRange(X25519_ENC_LENGTH, sealed.size)),
        )
    }

    private fun buildEncrypt(publicKeyBase64: String): HybridEncrypt {
        if (publicKeyBase64.isBlank()) {
            throw CryptoConfigurationException(
                "La clave pública HPKE de destino no está configurada (ver local.properties.example)",
            )
        }

        val rawKey = try {
            Base64.getDecoder().decode(publicKeyBase64.trim())
        } catch (e: IllegalArgumentException) {
            throw CryptoConfigurationException("La clave pública HPKE de destino no es base64 válido", e)
        }

        if (rawKey.size != X25519_KEY_LENGTH) {
            throw CryptoConfigurationException(
                "La clave pública HPKE de destino debe ser una clave X25519 de $X25519_KEY_LENGTH bytes",
            )
        }

        return try {
            HybridConfig.register()
            val publicKey = HpkePublicKey.create(HPKE_PARAMETERS, Bytes.copyFrom(rawKey), null)
            KeysetHandle.newBuilder()
                .addEntry(KeysetHandle.importKey(publicKey).withRandomId().makePrimary())
                .build()
                .getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        } catch (e: GeneralSecurityException) {
            throw CryptoConfigurationException("No se pudo inicializar HPKE con la clave pública de destino", e)
        }
    }

    private companion object {
        /** X25519 -> `enc` mide siempre 32 bytes; ahí se parte la salida de Tink. */
        const val X25519_ENC_LENGTH = 32
        const val X25519_KEY_LENGTH = 32

        val EMPTY_CONTEXT = ByteArray(0)
        val ENCODER: Base64.Encoder = Base64.getEncoder()

        val HPKE_PARAMETERS: HpkeParameters = HpkeParameters.builder()
            // NO_PREFIX: sin cabecera propia de Tink, para que el mensaje sea HPKE puro.
            .setVariant(HpkeParameters.Variant.NO_PREFIX)
            .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
            .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
            .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
            .build()
    }
}
