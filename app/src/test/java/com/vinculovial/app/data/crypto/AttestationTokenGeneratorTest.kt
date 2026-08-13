package com.vinculovial.app.data.crypto

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Los tests validan el token igual que lo hará el Gateway: misma PSK, mismo algoritmo.
 */
class AttestationTokenGeneratorTest {

    private val psk = "psk-de-desarrollo-de-al-menos-32-bytes"
    private val packageName = "com.vinculovial.app"
    private val now = Instant.parse("2026-08-12T10:00:00Z")
    private val ttlSeconds = 60L
    private val requestHash = "Zm9vYmFyLWhhc2gtZGUtcHJ1ZWJh"

    private fun generator(
        psk: String = this.psk,
        ttlSeconds: Long = this.ttlSeconds,
        clock: Clock = Clock.fixed(now, ZoneOffset.UTC),
    ) = AttestationTokenGenerator(
        psk = psk,
        ttlSeconds = ttlSeconds,
        packageName = packageName,
        clock = clock,
    )

    private fun parse(token: String, psk: String = this.psk, at: Instant = now.plusSeconds(1)) =
        Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(psk.toByteArray()))
            .clock { java.util.Date.from(at) }
            .requireIssuer(AttestationTokenGenerator.ISSUER)
            .requireAudience(AttestationTokenGenerator.AUDIENCE)
            .build()
            .parseSignedClaims(token)
            .payload

    @Test
    fun `el gateway puede validar firma emisor y audiencia`() {
        val claims = parse(generator().generate(requestHash))

        assertEquals(AttestationTokenGenerator.ISSUER, claims.issuer)
        assertTrue(claims.audience.contains(AttestationTokenGenerator.AUDIENCE))
    }

    @Test
    fun `incluye los veredictos de integridad esperados`() {
        val claims = parse(generator().generate(requestHash))

        @Suppress("UNCHECKED_CAST")
        val appIntegrity = claims[AttestationTokenGenerator.CLAIM_APP_INTEGRITY] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val deviceIntegrity = claims[AttestationTokenGenerator.CLAIM_DEVICE_INTEGRITY] as Map<String, Any>

        assertEquals(
            AttestationTokenGenerator.VERDICT_PLAY_RECOGNIZED,
            appIntegrity["appRecognitionVerdict"],
        )
        assertEquals(packageName, appIntegrity["packageName"])
        assertEquals(
            listOf(AttestationTokenGenerator.VERDICT_MEETS_DEVICE_INTEGRITY),
            deviceIntegrity["deviceRecognitionVerdict"],
        )
    }

    @Test
    fun `expira exactamente al cabo del ttl configurado`() {
        val claims = parse(generator().generate(requestHash))

        assertEquals(now.epochSecond, claims.issuedAt.toInstant().epochSecond)
        assertEquals(now.plusSeconds(ttlSeconds).epochSecond, claims.expiration.toInstant().epochSecond)
    }

    @Test
    fun `un token vencido es rechazado`() {
        val token = generator().generate(requestHash)

        assertThrows(ExpiredJwtException::class.java) {
            parse(token, at = now.plus(Duration.ofSeconds(ttlSeconds + 5)))
        }
    }

    @Test
    fun `una psk distinta invalida la firma`() {
        val token = generator().generate(requestHash)

        assertThrows(SignatureException::class.java) {
            parse(token, psk = "otra-psk-de-desarrollo-de-32-bytes-o-mas")
        }
    }

    @Test
    fun `cada token es unico`() {
        val generator = generator()

        val primero = parse(generator.generate(requestHash))
        val segundo = parse(generator.generate(requestHash))

        assertNotEquals(primero.id, segundo.id)
    }

    @Test
    fun `el token queda ligado al cuerpo de la request`() {
        val claims = parse(generator().generate(requestHash))

        @Suppress("UNCHECKED_CAST")
        val requestDetails = claims[AttestationTokenGenerator.CLAIM_REQUEST_DETAILS] as Map<String, Any>

        assertEquals(requestHash, requestDetails["requestHash"])
    }

    @Test
    fun `una psk corta es un error de configuracion`() {
        val error = assertThrows(CryptoConfigurationException::class.java) {
            generator(psk = "psk-corta")
        }

        assertTrue(error.message!!.contains("APP_ATTESTATION_PSK"))
        assertTrue("el mensaje no debe filtrar la PSK", !error.message!!.contains("psk-corta"))
    }

    @Test
    fun `el token no lleva datos personales`() {
        val token = generator().generate(requestHash)

        val payload = String(java.util.Base64.getUrlDecoder().decode(token.split(".")[1]))
        listOf("dni", "email", "password").forEach {
            assertTrue("el token incluyó '$it'", !payload.lowercase().contains(it))
        }
    }
}
