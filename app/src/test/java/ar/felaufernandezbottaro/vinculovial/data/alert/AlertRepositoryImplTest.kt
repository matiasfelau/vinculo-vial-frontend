package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.edge.SecureImageBuffer
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceBuffers
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceEncryptor
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidenceRole
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertResult
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
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Base64

/**
 * Multipart de `POST /alerts`: qué partes lleva cada tipo, cómo se cifran las fotos para IA con
 * el `attempt_id` como `info`, y el mapeo de cada respuesta del Gateway a resultado de dominio.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertRepositoryImplTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun registerTink() {
            HybridConfig.register()
            AeadConfig.register()
        }
    }

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var api: FakeAlertApi

    private val iaKeyset: KeysetHandle = KeysetHandle.generateNew(
        KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
    )
    private val otherKeyset: KeysetHandle = KeysetHandle.generateNew(
        KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
    )

    private val attemptId = "3f2b8c1e-0000-4000-8000-000000000001"
    private val patEnc = "cGF0LWVuYy1kZS1wcnVlYmE="

    @Before
    fun setUp() {
        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        tokenStore.save(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
        api = FakeAlertApi()
    }

    private fun repository(iaPublicKey: String = iaKeyset.publicKeyBase64()) = AlertRepositoryImpl(
        api = api,
        evidenceEncryptor = EvidenceEncryptor(HpkeEncryptor(), iaPublicKey),
        tokenStore = tokenStore,
        dispatcher = UnconfinedTestDispatcher(),
        moshi = Moshi.Builder().build(),
    )

    /** Una foto distinta por casillero, para poder reconocerla al descifrar. */
    private fun jpeg(role: EvidenceRole, index: Int) = ByteArray(64) { (role.ordinal * 16 + index + it % 7).toByte() }

    private fun evidence(vararg roles: Pair<EvidenceRole, Int>): EvidenceBuffers = EvidenceBuffers().also { buffers ->
        roles.forEach { (role, count) ->
            repeat(count) { index -> buffers.put(role, index, SecureImageBuffer.wrap(jpeg(role, index))) }
        }
    }

    private fun crashedEvidence(details: Int = 1) = evidence(
        EvidenceRole.GENERAL to 1,
        EvidenceRole.PLATE to 1,
        EvidenceRole.DETAIL to details,
    )

    private fun garageEvidence() = evidence(
        EvidenceRole.GENERAL to 1,
        EvidenceRole.PLATE to 1,
        EvidenceRole.CONTEXT to 1,
    )

    // -- Partes según el tipo ---------------------------------------------------------------

    @Test
    fun `CRASHED lleva metadata, general, plate y de 1 a 3 detail`() = runTest {
        listOf(1, 2, 3).forEach { details ->
            repository().sendAlert(attemptId, patEnc, AlertType.CRASHED, crashedEvidence(details))

            assertEquals(
                listOf("metadata", "general", "plate") + List(details) { "detail" },
                api.lastParts.map { it.partName() },
            )
        }
    }

    @Test
    fun `PARKED_ON_GARAGE lleva metadata, general, plate y context`() = runTest {
        repository().sendAlert(attemptId, patEnc, AlertType.PARKED_ON_GARAGE, garageEvidence())

        assertEquals(listOf("metadata", "general", "plate", "context"), api.lastParts.map { it.partName() })
    }

    @Test
    fun `metadata es application json con attempt_id, alert_type y el pat_enc recibido`() = runTest {
        repository().sendAlert(attemptId, patEnc, AlertType.PARKED_ON_GARAGE, garageEvidence())

        val metadata = api.lastParts.first()
        assertEquals("application/json", metadata.partContentType())
        val json = JSONObject(String(metadata.partBytes(), Charsets.UTF_8))
        assertEquals(3, json.length())
        assertEquals(attemptId, json.getString("attempt_id"))
        assertEquals("PARKED_ON_GARAGE", json.getString("alert_type"))
        assertEquals(patEnc, json.getString("pat_enc"))
    }

    @Test
    fun `las fotos van como application octet-stream`() = runTest {
        repository().sendAlert(attemptId, patEnc, AlertType.CRASHED, crashedEvidence(2))

        assertEquals(
            List(4) { "application/octet-stream" },
            api.lastParts.drop(1).map { it.partContentType() },
        )
    }

    @Test
    fun `manda el access token como bearer`() = runTest {
        repository().sendAlert(attemptId, patEnc, AlertType.CRASHED, crashedEvidence())

        assertEquals("Bearer access-abc", api.lastAuthorization)
    }

    // -- Cifrado para IA con el attempt_id --------------------------------------------------

    @Test
    fun `cada foto se cifra para IA con el attempt_id como info y descifra a la foto original`() = runTest {
        repository().sendAlert(attemptId, patEnc, AlertType.CRASHED, crashedEvidence(2))

        val photos = api.lastParts.drop(1)
        val expected = listOf(
            jpeg(EvidenceRole.GENERAL, 0),
            jpeg(EvidenceRole.PLATE, 0),
            jpeg(EvidenceRole.DETAIL, 0),
            jpeg(EvidenceRole.DETAIL, 1),
        )
        photos.zip(expected).forEach { (part, original) ->
            assertArrayEquals(original, decrypt(iaKeyset, part.partBytes(), attemptId))
        }
    }

    @Test
    fun `las fotos no se pueden abrir con otra clave ni con otro attempt_id`() = runTest {
        repository().sendAlert(attemptId, patEnc, AlertType.PARKED_ON_GARAGE, garageEvidence())

        val photo = api.lastParts[1].partBytes()
        assertThrows(GeneralSecurityException::class.java) { decrypt(otherKeyset, photo, attemptId) }
        assertThrows(GeneralSecurityException::class.java) { decrypt(iaKeyset, photo, "otro-intento") }
    }

    @Test
    fun `un envio con otro attempt_id sale re-cifrado con ese attempt_id`() = runTest {
        val evidence = crashedEvidence()
        val repository = repository()

        repository.sendAlert("intento-viejo", patEnc, AlertType.CRASHED, evidence)
        val first = api.lastParts.drop(1).map { it.partBytes() }
        repository.sendAlert("intento-nuevo", patEnc, AlertType.CRASHED, evidence)
        val second = api.lastParts.drop(1).map { it.partBytes() }

        first.zip(second).forEach { (old, new) ->
            assertFalse(old.contentEquals(new))
            assertThrows(GeneralSecurityException::class.java) { decrypt(iaKeyset, new, "intento-viejo") }
            decrypt(iaKeyset, new, "intento-nuevo")
        }
    }

    @Test
    fun `no libera las fotos en claro porque hacen falta para reenviar`() = runTest {
        val evidence = crashedEvidence()

        repository().sendAlert(attemptId, patEnc, AlertType.CRASHED, evidence)

        assertArrayEquals(jpeg(EvidenceRole.GENERAL, 0), evidence.read(EvidenceRole.GENERAL, 0) { it.copyOf() })
    }

    // -- Evidencia que no corresponde al tipo (error de programacion) -----------------------

    @Test
    fun `si falta una foto del tipo no llama a la red`() = runTest {
        val missingPlate = evidence(EvidenceRole.GENERAL to 1, EvidenceRole.DETAIL to 1)

        val result = repository().sendAlert(attemptId, patEnc, AlertType.CRASHED, missingPlate)

        assertEquals(SendAlertResult.Failed(SendAlertError.Unexpected), result)
        assertEquals(0, api.callCount)
    }

    @Test
    fun `si sobran fotos de daño no llama a la red`() = runTest {
        val result = repository().sendAlert(attemptId, patEnc, AlertType.CRASHED, crashedEvidence(details = 4))

        assertEquals(SendAlertResult.Failed(SendAlertError.Unexpected), result)
        assertEquals(0, api.callCount)
    }

    @Test
    fun `un tipo deshabilitado no llama a la red`() = runTest {
        val result = repository().sendAlert(attemptId, patEnc, AlertType.PARKED_TOO_CLOSE, garageEvidence())

        assertEquals(SendAlertResult.Failed(SendAlertError.Unexpected), result)
        assertEquals(0, api.callCount)
    }

    @Test
    fun `sin clave de IA configurada es Unexpected y no llama a la red`() = runTest {
        val result = repository(iaPublicKey = "").sendAlert(attemptId, patEnc, AlertType.CRASHED, crashedEvidence())

        assertEquals(SendAlertResult.Failed(SendAlertError.Unexpected), result)
        assertEquals(0, api.callCount)
    }

    @Test
    fun `sin sesion guardada no llama a la red`() = runTest {
        tokenStore.clear()

        val result = repository().sendAlert(attemptId, patEnc, AlertType.CRASHED, crashedEvidence())

        assertEquals(SendAlertResult.Failed(SendAlertError.Unexpected), result)
        assertEquals(0, api.callCount)
    }

    // -- Respuestas -------------------------------------------------------------------------

    @Test
    fun `202 es Sent`() = runTest {
        assertEquals(SendAlertResult.Sent, send())
    }

    @Test
    fun `400 se traduce segun el code`() = runTest {
        val cases = mapOf(
            "INVALID_EVIDENCE" to SendAlertError.InvalidEvidence,
            "ALERT_TYPE_DISABLED" to SendAlertError.AlertTypeDisabled,
            "INVALID_ALERT_TYPE" to SendAlertError.InvalidAlertType,
            "OTRO_CODE" to SendAlertError.InvalidData,
        )
        cases.forEach { (code, expected) ->
            api.response = errorResponse(400, """{"error":"x","code":"$code"}""")

            assertEquals("code $code", SendAlertResult.Failed(expected), send())
        }
    }

    @Test
    fun `400 sin cuerpo o con cuerpo ilegible es InvalidData`() = runTest {
        listOf("", "no es json", """{"code":7}""").forEach { body ->
            api.response = errorResponse(400, body)

            assertEquals("cuerpo '$body'", SendAlertResult.Failed(SendAlertError.InvalidData), send())
        }
    }

    @Test
    fun `413 es EvidenceTooLarge con o sin code, el de Kong no trae code`() = runTest {
        listOf("""{"error":"x","code":"EVIDENCE_TOO_LARGE"}""", "").forEach { body ->
            api.response = errorResponse(413, body)

            assertEquals(SendAlertResult.Failed(SendAlertError.EvidenceTooLarge), send())
        }
    }

    @Test
    fun `403 ISSUER_UNDER_REVIEW es UnderReview y otro 403 es inesperado`() = runTest {
        api.response = errorResponse(403, """{"error":"x","code":"ISSUER_UNDER_REVIEW"}""")
        assertEquals(SendAlertResult.Failed(SendAlertError.UnderReview), send())

        api.response = errorResponse(403, """{"error":"x","code":"OTRO"}""")
        assertEquals(SendAlertResult.Failed(SendAlertError.Unexpected), send())
    }

    @Test
    fun `404 ATTEMPT_NOT_FOUND es AttemptNotFound y otro 404 es inesperado`() = runTest {
        api.response = errorResponse(404, """{"error":"x","code":"ATTEMPT_NOT_FOUND"}""")
        assertEquals(SendAlertResult.Failed(SendAlertError.AttemptNotFound), send())

        api.response = errorResponse(404, """{"error":"x","code":"NOT_FOUND"}""")
        assertEquals(SendAlertResult.Failed(SendAlertError.Unexpected), send())
    }

    @Test
    fun `410 ATTEMPT_EXPIRED es AttemptExpired y otro 410 es inesperado`() = runTest {
        api.response = errorResponse(410, """{"error":"x","code":"ATTEMPT_EXPIRED"}""")
        assertEquals(SendAlertResult.Failed(SendAlertError.AttemptExpired), send())

        api.response = errorResponse(410, """{"error":"x","code":"EVIDENCE_EXPIRED"}""")
        assertEquals(SendAlertResult.Failed(SendAlertError.Unexpected), send())
    }

    @Test
    fun `429 es RateLimited`() = runTest {
        api.response = errorResponse(429, "")

        assertEquals(SendAlertResult.Failed(SendAlertError.RateLimited), send())
    }

    @Test
    fun `502, 503 y 504 son Unavailable`() = runTest {
        listOf(502, 503, 504).forEach { status ->
            api.response = errorResponse(status, "")

            assertEquals("status $status", SendAlertResult.Failed(SendAlertError.Unavailable), send())
        }
    }

    @Test
    fun `500 y 401 son un error inesperado`() = runTest {
        listOf(500, 401).forEach { status ->
            api.response = errorResponse(status, "")

            assertEquals("status $status", SendAlertResult.Failed(SendAlertError.Unexpected), send())
        }
    }

    @Test
    fun `circuito abierto es Unavailable, no Network`() = runTest {
        api.exceptionToThrow = CircuitBreakerOpenException("alerts")

        assertEquals(SendAlertResult.Failed(SendAlertError.Unavailable), send())
    }

    @Test
    fun `un fallo de red es Network`() = runTest {
        api.exceptionToThrow = IOException("sin conexion")

        assertEquals(SendAlertResult.Failed(SendAlertError.Network), send())
    }

    @Test
    fun `una excepcion inesperada es Unexpected`() = runTest {
        api.exceptionToThrow = IllegalStateException("boom")

        assertEquals(SendAlertResult.Failed(SendAlertError.Unexpected), send())
    }

    private suspend fun send(): SendAlertResult =
        repository().sendAlert(attemptId, patEnc, AlertType.CRASHED, crashedEvidence())

    private fun errorResponse(code: Int, body: String) =
        Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType()))

    private fun KeysetHandle.publicKeyBase64(): String = Base64.getEncoder().encodeToString(
        (publicKeysetHandle.primary.key as HpkePublicKey).publicKeyBytes.toByteArray(),
    )

    private fun decrypt(keyset: KeysetHandle, sealed: ByteArray, info: String): ByteArray =
        keyset.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
            .decrypt(sealed, info.toByteArray(Charsets.UTF_8))
}
