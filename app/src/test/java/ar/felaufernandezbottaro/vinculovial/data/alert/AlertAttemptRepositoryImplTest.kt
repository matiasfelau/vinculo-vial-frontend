package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.AlertAttemptResponseDto
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptResult
import com.google.crypto.tink.Aead
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.Instant
import java.util.Base64

/**
 * Cubre la llamada 1 de US-08: la patente se cifra directa contra la clave de **Vehicle**
 * (nunca la de IA) y cada código de respuesta del Gateway mapea al resultado de dominio correcto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertAttemptRepositoryImplTest {

    companion object {
        // @BeforeClass, no @Before: los keyset de acá abajo son field initializers, que
        // corren en el constructor -- antes de cualquier @Before.
        @BeforeClass
        @JvmStatic
        fun registerTink() {
            HybridConfig.register()
            AeadConfig.register()
        }
    }

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var api: FakeAlertAttemptApi

    private val vehiclePrivateKeyset: KeysetHandle = KeysetHandle.generateNew(
        KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
    )
    private val vehiclePublicKeyBase64 = vehiclePrivateKeyset.publicKeyBase64()

    /** Par de IA: jamás se le pasa al repositorio, sólo sirve para comprobar que no abre el pat_enc. */
    private val iaPrivateKeyset: KeysetHandle = KeysetHandle.generateNew(
        KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
    )

    private val patente = "GUQ856"

    @Before
    fun setUp() {
        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        tokenStore.save(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
        api = FakeAlertAttemptApi()
    }

    private fun repository(vehiclePublicKey: String = vehiclePublicKeyBase64) = AlertAttemptRepositoryImpl(
        api = api,
        encryptor = HpkeEncryptor(),
        vehiclePublicKey = vehiclePublicKey,
        tokenStore = tokenStore,
        dispatcher = UnconfinedTestDispatcher(),
        moshi = Moshi.Builder().build(),
    )

    @Test
    fun `el pat_enc solo lo puede abrir vehicle y contiene la patente en claro`() = runTest {
        repository().startAttempt(patente)

        val patEnc = api.lastRequest!!.patEnc
        assertEquals(patente, String(decrypt(vehiclePrivateKeyset, patEnc)))
    }

    @Test
    fun `el pat_enc no lo puede abrir la clave de IA`() = runTest {
        repository().startAttempt(patente)

        val patEnc = api.lastRequest!!.patEnc
        assertThrows(GeneralSecurityException::class.java) { decrypt(iaPrivateKeyset, patEnc) }
    }

    @Test
    fun `manda el access token como bearer`() = runTest {
        repository().startAttempt(patente)

        assertEquals("Bearer access-abc", api.lastAuthorization)
    }

    @Test
    fun `201 devuelve Started con el attempt_id, el vencimiento y el mismo pat_enc que se envio`() = runTest {
        api.response = Response.success(
            201,
            AlertAttemptResponseDto(attemptId = "attempt-1", expiresAt = "2030-01-01T00:10:00Z"),
        )

        val result = repository().startAttempt(patente)

        assertEquals(
            StartAlertAttemptResult.Started(
                attemptId = "attempt-1",
                expiresAt = Instant.parse("2030-01-01T00:10:00Z"),
                patEnc = api.lastRequest!!.patEnc,
            ),
            result,
        )
    }

    @Test
    fun `201 acepta un expires_at RFC3339 con offset y fraccion de segundo`() = runTest {
        api.response = Response.success(
            201,
            AlertAttemptResponseDto(attemptId = "attempt-1", expiresAt = "2030-01-01T00:10:00.123456-03:00"),
        )

        val result = repository().startAttempt(patente) as StartAlertAttemptResult.Started

        assertEquals(Instant.parse("2030-01-01T03:10:00.123456Z"), result.expiresAt)
    }

    @Test
    fun `201 con un expires_at ilegible es Unexpected`() = runTest {
        api.response = Response.success(
            201,
            AlertAttemptResponseDto(attemptId = "attempt-1", expiresAt = "manana"),
        )

        assertEquals(StartAlertAttemptResult.Unexpected, repository().startAttempt(patente))
    }

    @Test
    fun `201 sin cuerpo es Unexpected`() = runTest {
        api.response = Response.success<AlertAttemptResponseDto>(201, null)

        assertEquals(StartAlertAttemptResult.Unexpected, repository().startAttempt(patente))
    }

    @Test
    fun `404 es NotInNetwork`() = runTest {
        api.response = errorResponse(404, """{"error":"sin dueños","code":"NOT_IN_NETWORK"}""")

        assertEquals(StartAlertAttemptResult.NotInNetwork, repository().startAttempt(patente))
    }

    @Test
    fun `403 ISSUER_UNDER_REVIEW es UnderReview`() = runTest {
        api.response = errorResponse(403, """{"error":"en revision","code":"ISSUER_UNDER_REVIEW"}""")

        assertEquals(StartAlertAttemptResult.UnderReview, repository().startAttempt(patente))
    }

    @Test
    fun `403 con otro code es Unexpected`() = runTest {
        api.response = errorResponse(403, """{"error":"prohibido","code":"OTRO"}""")

        assertEquals(StartAlertAttemptResult.Unexpected, repository().startAttempt(patente))
    }

    @Test
    fun `403 sin cuerpo parseable es Unexpected`() = runTest {
        api.response = errorResponse(403, "no es json")

        assertEquals(StartAlertAttemptResult.Unexpected, repository().startAttempt(patente))
    }

    @Test
    fun `400 es InvalidPlate`() = runTest {
        api.response = errorResponse(400, """{"error":"patente invalida","code":"INVALID_PLATE_CRYPTOGRAM"}""")

        assertEquals(StartAlertAttemptResult.InvalidPlate, repository().startAttempt(patente))
    }

    @Test
    fun `429 es RateLimited`() = runTest {
        api.response = errorResponse(429)

        assertEquals(StartAlertAttemptResult.RateLimited, repository().startAttempt(patente))
    }

    @Test
    fun `502, 503 y 504 son Unavailable`() = runTest {
        listOf(502, 503, 504).forEach { status ->
            api.response = errorResponse(status)

            assertEquals("status $status", StartAlertAttemptResult.Unavailable, repository().startAttempt(patente))
        }
    }

    @Test
    fun `500 y 401 son un error inesperado`() = runTest {
        listOf(500, 401).forEach { status ->
            api.response = errorResponse(status)

            assertEquals("status $status", StartAlertAttemptResult.Unexpected, repository().startAttempt(patente))
        }
    }

    @Test
    fun `circuito abierto es Unavailable`() = runTest {
        api.exceptionToThrow = CircuitBreakerOpenException("alerts")

        assertEquals(StartAlertAttemptResult.Unavailable, repository().startAttempt(patente))
    }

    @Test
    fun `un fallo de red es Unavailable`() = runTest {
        api.exceptionToThrow = IOException("sin conexion")

        assertEquals(StartAlertAttemptResult.Unavailable, repository().startAttempt(patente))
    }

    @Test
    fun `una excepcion inesperada es Unexpected`() = runTest {
        api.exceptionToThrow = IllegalStateException("boom")

        assertEquals(StartAlertAttemptResult.Unexpected, repository().startAttempt(patente))
    }

    @Test
    fun `sin sesion guardada no llama a la red`() = runTest {
        tokenStore.clear()

        val result = repository().startAttempt(patente)

        assertEquals(StartAlertAttemptResult.Unexpected, result)
        assertEquals(0, api.callCount)
    }

    @Test
    fun `clave de vehicle mal configurada es Unexpected y no llama a la red`() = runTest {
        val result = repository(vehiclePublicKey = "").startAttempt(patente)

        assertEquals(StartAlertAttemptResult.Unexpected, result)
        assertEquals(0, api.callCount)
    }

    @Test
    fun `el toString de Started no filtra el pat_enc`() {
        val started = StartAlertAttemptResult.Started("attempt-1", Instant.EPOCH, "pat-enc-secreto")

        assertTrue(!started.toString().contains("pat-enc-secreto"))
    }

    private fun errorResponse(code: Int, body: String = "") =
        Response.error<AlertAttemptResponseDto>(code, body.toResponseBody("application/json".toMediaType()))

    private fun KeysetHandle.publicKeyBase64(): String = Base64.getEncoder().encodeToString(
        (publicKeysetHandle.primary.key as HpkePublicKey).publicKeyBytes.toByteArray(),
    )

    private fun decrypt(privateKeyset: KeysetHandle, cryptogram: String): ByteArray {
        val sealed = Base64.getDecoder().decode(cryptogram)
        val decrypt = privateKeyset.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        return decrypt.decrypt(sealed, ByteArray(0))
    }
}
