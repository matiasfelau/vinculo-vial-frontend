package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.edge.SecureImageBuffer
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceBuffers
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceEncryptor
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidenceRole
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertResult
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptResult
import com.google.crypto.tink.Aead
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
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Base64

/**
 * Contrato de `POST /alerts` y de `POST /alerts/attempts` de punta a punta dentro de la app:
 * repositorio real, Retrofit real y un servidor HTTP simulado. Lo que se verifica es lo que
 * sale por el cable, no lo que arma el código: nombre de cada parte, `Content-Type` de cada una
 * y cómo se interpretan las respuestas, incluido el 504 nativo de Kong (sin el cuerpo del contrato).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertWireContractTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun registerTink() {
            HybridConfig.register()
            AeadConfig.register()
        }

        /** enc (32) + AES-GCM tag (16) de HPKE alrededor del JPEG. */
        private const val HPKE_OVERHEAD_BYTES = 48
    }

    private lateinit var server: MockWebServer
    private lateinit var alertRepository: AlertRepositoryImpl
    private lateinit var attemptRepository: AlertAttemptRepositoryImpl

    private val attemptId = "3f2b8c1e-0000-4000-8000-000000000001"
    private val patEnc = "cGF0LWVuYy1kZS1wcnVlYmE="
    private val jpegSize = 64

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        val tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        tokenStore.save(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))

        val moshi = Moshi.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val keyset = KeysetHandle.generateNew(
            KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
        )
        val publicKey = Base64.getEncoder().encodeToString(
            (keyset.publicKeysetHandle.primary.key as HpkePublicKey).publicKeyBytes.toByteArray(),
        )
        // Acá sólo importa la forma de lo que sale por el cable.
        alertRepository = AlertRepositoryImpl(
            api = retrofit.create(AlertApi::class.java),
            evidenceEncryptor = EvidenceEncryptor(HpkeEncryptor(), publicKey),
            tokenStore = tokenStore,
            dispatcher = UnconfinedTestDispatcher(),
            moshi = moshi,
        )
        attemptRepository = AlertAttemptRepositoryImpl(
            api = retrofit.create(AlertAttemptApi::class.java),
            tokenStore = tokenStore,
            dispatcher = UnconfinedTestDispatcher(),
            moshi = moshi,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun evidence(vararg roles: Pair<EvidenceRole, Int>) = EvidenceBuffers().also { buffers ->
        roles.forEach { (role, count) ->
            repeat(count) { index -> buffers.put(role, index, SecureImageBuffer.wrap(ByteArray(jpegSize) { (index + 1).toByte() })) }
        }
    }

    private fun crashed(details: Int) = evidence(EvidenceRole.GENERAL to 1, EvidenceRole.PLATE to 1, EvidenceRole.DETAIL to details)

    private fun garage() = evidence(EvidenceRole.GENERAL to 1, EvidenceRole.PLATE to 1, EvidenceRole.CONTEXT to 1)

    // -- Nombre y Content-Type de cada parte, en el cable ------------------------------------

    @Test
    fun `CRASHED con 3 fotos de daño sale con las partes metadata, general, plate y detail x3`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        alertRepository.sendAlert(attemptId, patEnc,AlertType.CRASHED, crashed(details = 3))

        val parts = server.takeRequest().parseMultipartWire()
        assertEquals(listOf("metadata", "general", "plate", "detail", "detail", "detail"), parts.map { it.name })
    }

    @Test
    fun `PARKED_ON_GARAGE sale con las partes metadata, general, plate y context`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        alertRepository.sendAlert(attemptId, patEnc,AlertType.PARKED_ON_GARAGE, garage())

        val parts = server.takeRequest().parseMultipartWire()
        assertEquals(listOf("metadata", "general", "plate", "context"), parts.map { it.name })
    }

    @Test
    fun `el nombre de cada parte de foto es el rol en minuscula`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))
        alertRepository.sendAlert(attemptId, patEnc,AlertType.CRASHED, crashed(details = 2))
        val crashedPhotos = server.takeRequest().parseMultipartWire().drop(1).map { it.name }

        server.enqueue(MockResponse().setResponseCode(202))
        alertRepository.sendAlert(attemptId, patEnc,AlertType.PARKED_ON_GARAGE, garage())
        val garagePhotos = server.takeRequest().parseMultipartWire().drop(1).map { it.name }

        val roleNames = setOf("general", "plate", "detail", "context")
        assertTrue((crashedPhotos + garagePhotos).all { it in roleNames })
        assertTrue((crashedPhotos + garagePhotos).all { it == it.lowercase() })
        assertEquals(roleNames, (crashedPhotos + garagePhotos).toSet())
    }

    @Test
    fun `toda parte de foto sale con Content-Type application octet-stream exacto`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        alertRepository.sendAlert(attemptId, patEnc,AlertType.CRASHED, crashed(details = 3))

        val photos = server.takeRequest().parseMultipartWire().drop(1)
        assertEquals(5, photos.size)
        assertTrue(photos.all { it.contentType == "application/octet-stream" })
    }

    @Test
    fun `la parte metadata sale con Content-Type application json exacto`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        alertRepository.sendAlert(attemptId, patEnc,AlertType.CRASHED, crashed(details = 1))

        val metadata = server.takeRequest().parseMultipartWire().first()
        assertEquals("metadata", metadata.name)
        assertEquals("application/json", metadata.contentType)
        assertEquals(
            """{"attempt_id":"$attemptId","alert_type":"CRASHED","pat_enc":"$patEnc"}""",
            String(metadata.content, Charsets.UTF_8),
        )
    }

    @Test
    fun `el pat_enc viaja solo en el metadata, nunca como parte aparte`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        alertRepository.sendAlert(attemptId, patEnc, AlertType.PARKED_ON_GARAGE, garage())

        val parts = server.takeRequest().parseMultipartWire()
        assertEquals(1, parts.count { String(it.content, Charsets.ISO_8859_1).contains(patEnc) })
        assertEquals("metadata", parts.single { String(it.content, Charsets.ISO_8859_1).contains(patEnc) }.name)
    }

    // -- Errores de Vehicle durante el envio: genericos, sin mencionar la patente -------------

    @Test
    fun `un 503 de Vehicle durante el envio es Unavailable`() = runTest {
        server.enqueue(jsonUnavailable(503))

        val result = alertRepository.sendAlert(attemptId, patEnc, AlertType.CRASHED, crashed(1))

        assertEquals(SendAlertResult.Failed(SendAlertError.Unavailable), result)
    }

    @Test
    fun `un 400 INVALID_PLATE_CRYPTOGRAM durante el envio es un error generico de datos`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"patente invalida","code":"INVALID_PLATE_CRYPTOGRAM"}"""),
        )

        val result = alertRepository.sendAlert(attemptId, patEnc, AlertType.CRASHED, crashed(1))

        assertEquals(SendAlertResult.Failed(SendAlertError.InvalidData), result)
    }

    // -- 410: el reintento del intento vuelve a pedir uno, sin body ---------------------------

    @Test
    fun `renovar el intento es la misma llamada sin body y devuelve el attempt_id nuevo`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{"attempt_id":"nuevo-attempt","expires_at":"2030-01-01T00:10:00Z"}"""),
        )

        val result = attemptRepository.startAttempt()

        val request = server.takeRequest()
        assertEquals("/api/v1/alerts/attempts", request.path)
        assertEquals(0L, request.bodySize)
        assertTrue(result is StartAlertAttemptResult.Started)
        assertEquals("nuevo-attempt", (result as StartAlertAttemptResult.Started).attemptId)
    }

    @Test
    fun `las fotos viajan cifradas, con el tamaño del JPEG mas el overhead de HPKE`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        alertRepository.sendAlert(attemptId, patEnc,AlertType.PARKED_ON_GARAGE, garage())

        val photos = server.takeRequest().parseMultipartWire().drop(1)
        assertTrue(photos.all { it.content.size == jpegSize + HPKE_OVERHEAD_BYTES })
        assertTrue(photos.all { part -> part.filename?.endsWith(".bin") == true })
    }

    @Test
    fun `el request es multipart form-data con el bearer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        alertRepository.sendAlert(attemptId, patEnc,AlertType.CRASHED, crashed(details = 1))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/alerts", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data; boundary="))
    }

    // -- 502, 503 y 504 son la misma falla transitoria ---------------------------------------

    /** El 504 nativo de Kong ante un timeout del upstream: texto plano, sin el cuerpo del contrato. */
    private fun kongGatewayTimeout() = MockResponse()
        .setResponseCode(504)
        .setHeader("Content-Type", "text/plain; charset=utf-8")
        .setBody("The upstream server is timing out")

    private fun jsonUnavailable(status: Int) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"error":"no disponible","code":"DEPENDENCY_UNAVAILABLE"}""")

    @Test
    fun `un 504 nativo de Kong al enviar es Unavailable, igual que un 502 y un 503`() = runTest {
        server.enqueue(kongGatewayTimeout())
        val gatewayTimeout = alertRepository.sendAlert(attemptId, patEnc,AlertType.CRASHED, crashed(1))

        server.enqueue(jsonUnavailable(503))
        val unavailable = alertRepository.sendAlert(attemptId, patEnc,AlertType.CRASHED, crashed(1))

        server.enqueue(jsonUnavailable(502))
        val badGateway = alertRepository.sendAlert(attemptId, patEnc,AlertType.CRASHED, crashed(1))

        assertEquals(SendAlertResult.Failed(SendAlertError.Unavailable), gatewayTimeout)
        assertEquals(unavailable, gatewayTimeout)
        assertEquals(badGateway, gatewayTimeout)
    }

    @Test
    fun `un 504 nativo de Kong al abrir el intento es Unavailable, igual que un 502 y un 503`() = runTest {
        server.enqueue(kongGatewayTimeout())
        val gatewayTimeout = attemptRepository.startAttempt()

        server.enqueue(jsonUnavailable(503))
        val unavailable = attemptRepository.startAttempt()

        server.enqueue(jsonUnavailable(502))
        val badGateway = attemptRepository.startAttempt()

        assertEquals(StartAlertAttemptResult.Unavailable, gatewayTimeout)
        assertEquals(unavailable, gatewayTimeout)
        assertEquals(badGateway, gatewayTimeout)
    }
}
