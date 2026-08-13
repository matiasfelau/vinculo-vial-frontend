package com.vinculovial.app.data.crypto

import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.util.Bytes
import java.security.GeneralSecurityException
import java.util.Base64

/**
 * Mensaje HPKE listo para viajar por HTTP, tal como lo espera `vinculo-vial-identity`.
 *
 * Es el encoding estándar de HPKE modo BASE partido en sus dos mitades:
 * `enc` (clave pública efímera del KEM) y el ciphertext AEAD.
 */
data class HpkeMessage(
    val ephemeralPublicKey: String,
    val ciphertext: String,
)

/**
 * Cifra el payload de registro contra la Public Key X25519 de Identity.
 *
 * HPKE modo BASE — KEM DHKEM(X25519, HKDF-SHA256), KDF HKDF-SHA256, AEAD AES-256-GCM.
 * Cada llamada genera una clave efímera nueva: dos cifrados del mismo texto no se parecen.
 *
 * @param identityPublicKeyBase64 clave pública cruda (32 bytes) en base64 estándar.
 */
class HpkeEncryptor(
    private val identityPublicKeyBase64: String,
) {

    /** Se construye una sola vez: parsear la clave en cada request no aporta nada. */
    private val hybridEncrypt: HybridEncrypt by lazy { buildEncrypt() }

    /**
     * @param plaintext payload interno `{dni, email, password}` serializado.
     * @param contextInfo info asociada de HPKE; vacía por contrato con Identity.
     */
    fun encrypt(plaintext: ByteArray, contextInfo: ByteArray = EMPTY_CONTEXT): HpkeMessage {
        val sealed = try {
            hybridEncrypt.encrypt(plaintext, contextInfo)
        } catch (e: GeneralSecurityException) {
            throw CryptoOperationException("No se pudo cifrar el payload de registro", e)
        }

        if (sealed.size <= X25519_ENC_LENGTH) {
            throw CryptoOperationException("Salida HPKE más corta que la clave efímera")
        }

        return HpkeMessage(
            ephemeralPublicKey = ENCODER.encodeToString(sealed.copyOfRange(0, X25519_ENC_LENGTH)),
            ciphertext = ENCODER.encodeToString(sealed.copyOfRange(X25519_ENC_LENGTH, sealed.size)),
        )
    }

    private fun buildEncrypt(): HybridEncrypt {
        if (identityPublicKeyBase64.isBlank()) {
            throw CryptoConfigurationException(
                "IDENTITY_HPKE_PUBLIC_KEY no está configurada (ver local.properties.example)",
            )
        }

        val rawKey = try {
            Base64.getDecoder().decode(identityPublicKeyBase64.trim())
        } catch (e: IllegalArgumentException) {
            throw CryptoConfigurationException("IDENTITY_HPKE_PUBLIC_KEY no es base64 válido", e)
        }

        if (rawKey.size != X25519_KEY_LENGTH) {
            throw CryptoConfigurationException(
                "IDENTITY_HPKE_PUBLIC_KEY debe ser una clave X25519 de $X25519_KEY_LENGTH bytes",
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
            throw CryptoConfigurationException("No se pudo inicializar HPKE con la clave de Identity", e)
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
