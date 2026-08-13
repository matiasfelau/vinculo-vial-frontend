package com.vinculovial.app.data.crypto

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.time.Clock
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Mock local de Google Play Integrity: firma un JWT de vida corta con la PSK compartida
 * con el Gateway, imitando la forma del veredicto real.
 *
 * La integración real con Play Integrity está fuera del alcance de US-01; cuando llegue,
 * cambia esta clase y el Gateway pasa a validar contra Google, sin tocar el resto del flujo.
 *
 * El Gateway valida firma y expiración; Identity nunca ve este token.
 */
class AttestationTokenGenerator(
    psk: String,
    private val ttlSeconds: Long,
    private val packageName: String,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val signingKey: SecretKey = buildKey(psk)

    fun generate(): String {
        val issuedAt = clock.instant()
        val expiresAt = issuedAt.plusSeconds(ttlSeconds)

        return Jwts.builder()
            .issuer(ISSUER)
            .audience().add(AUDIENCE).and()
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            // Estructura espejo del payload de Play Integrity, para que migrar al
            // veredicto real no obligue a cambiar el contrato con el Gateway.
            .claim(
                CLAIM_APP_INTEGRITY,
                mapOf(
                    "appRecognitionVerdict" to VERDICT_PLAY_RECOGNIZED,
                    "packageName" to packageName,
                ),
            )
            .claim(
                CLAIM_DEVICE_INTEGRITY,
                mapOf("deviceRecognitionVerdict" to listOf(VERDICT_MEETS_DEVICE_INTEGRITY)),
            )
            .claim(
                CLAIM_REQUEST_DETAILS,
                mapOf(
                    "requestPackageName" to packageName,
                    "timestampMillis" to issuedAt.toEpochMilli().toString(),
                ),
            )
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact()
    }

    private fun buildKey(psk: String): SecretKey {
        val bytes = psk.toByteArray(Charsets.UTF_8)
        if (bytes.size < MIN_PSK_BYTES) {
            // El mensaje no incluye la PSK, sólo su longitud.
            throw CryptoConfigurationException(
                "APP_ATTESTATION_PSK debe tener al menos $MIN_PSK_BYTES bytes para HMAC-SHA256 " +
                    "(actual: ${bytes.size})",
            )
        }
        return Keys.hmacShaKeyFor(bytes)
    }

    companion object {
        const val ISSUER = "vinculo-vial-app"
        const val AUDIENCE = "vinculo-vial-gateway"

        const val CLAIM_APP_INTEGRITY = "appIntegrity"
        const val CLAIM_DEVICE_INTEGRITY = "deviceIntegrity"
        const val CLAIM_REQUEST_DETAILS = "requestDetails"

        const val VERDICT_PLAY_RECOGNIZED = "PLAY_RECOGNIZED"
        const val VERDICT_MEETS_DEVICE_INTEGRITY = "MEETS_DEVICE_INTEGRITY"

        /** HMAC-SHA256 exige una clave de al menos 256 bits. */
        const val MIN_PSK_BYTES = 32
    }
}
