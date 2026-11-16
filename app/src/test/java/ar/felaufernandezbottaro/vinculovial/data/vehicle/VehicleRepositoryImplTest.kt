package ar.felaufernandezbottaro.vinculovial.data.vehicle

import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSessionKeyVault
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleResult
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.VehicleCount
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Base64

/**
 * Contrato de US-14 contra el Gateway simulado: endpoint, verbo y `Authorization` de cada pedido,
 * el payload HPKE exacto (`{ "patente" }` con la patente en base64, sin chasis), el conteo de la
 * respuesta y el mapeo de cada `code` de error. Ninguna respuesta trae patentes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var api: VehicleApi
    private lateinit var tokenStore: SecureTokenStore

    private lateinit var vehicleKeyset: KeysetHandle
    private lateinit var vehiclePublicKey: String

    private var forceCircuitOpen = false
    private val plate = "AB123CD"

    @Before
    fun setUp() {
        HybridConfig.register()
        vehicleKeyset = KeysetHandle.generateNew(
            KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
        )
        vehiclePublicKey = Base64.getEncoder().encodeToString(
            (vehicleKeyset.publicKeysetHandle.primary.key as HpkePublicKey).publicKeyBytes.toByteArray(),
        )
        tokenStore = SecureTokenStore(FakeSharedPreferences(), FakeSessionKeyVault())
        tokenStore.startSession(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))

        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                if (forceCircuitOpen) throw CircuitBreakerOpenException("vehicles") else chain.proceed(chain.request())
            }
            .build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(VehicleApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun TestScope.repository(
        publicKey: String = vehiclePublicKey,
        vehicleApi: VehicleApi = api,
    ) = VehicleRepositoryImpl(
        api = vehicleApi,
        encryptor = HpkeEncryptor(),
        vehiclePublicKey = publicKey,
        tokenStore = tokenStore,
        moshi = Moshi.Builder().build(),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private fun count(count: Int, max: Int = 3) = """{"count":$count,"max":$max}"""

    private fun error(code: String) = """{"error":"mensaje","code":"$code"}"""

    /** Lo que Vehicle ve al descifrar el `payload_enc`: `info` vacío, igual que el resto. */
    private fun decryptPayload(request: RecordedRequest): JSONObject {
        val body = JSONObject(request.body.readUtf8())
        val sealed = Base64.getDecoder().decode(body.getString("payload_enc"))
        val plaintext = vehicleKeyset
            .getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
            .decrypt(sealed, ByteArray(0))
        return JSONObject(String(plaintext))
    }

    // --- Vincular ---------------------------------------------------------------------------

    @Test
    fun `vincular publica en vehicles con el access token y devuelve el conteo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(count(1)))

        val result = repository().link(plate)

        assertEquals(VehicleResult.Success(VehicleCount(count = 1, max = 3)), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/vehicles", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
    }

    @Test
    fun `el cuerpo de vincular es solo el payload_enc`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(count(1)))

        repository().link(plate)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(setOf("payload_enc"), body.keys().asSequence().toSet())
    }

    @Test
    fun `el payload cifrado lleva solo la patente en base64, sin chasis`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(count(1)))

        repository().link(plate)

        val payload = decryptPayload(server.takeRequest())
        // Vehicle tipa `patente` como []byte: encoding/json de Go la exige en base64.
        assertEquals(setOf("patente"), payload.keys().asSequence().toSet())
        assertEquals(plate, String(Base64.getDecoder().decode(payload.getString("patente"))))
    }

    @Test
    fun `la patente no viaja en claro`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(count(1)))

        repository().link(plate)

        val raw = server.takeRequest().body.readUtf8()
        assertFalse(raw.contains(plate))
        assertFalse(raw.contains(Base64.getEncoder().encodeToString(plate.toByteArray())))
    }

    @Test
    fun `un 201 sin cuerpo no se toma por un conteo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        // Nunca un éxito con un conteo inventado: la app no muestra un número que el servidor no dio.
        assertTrue(repository().link(plate) is VehicleResult.Failed)
    }

    // --- Contar -----------------------------------------------------------------------------

    @Test
    fun `contar hace GET a vehicles y devuelve count y max`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(count(2)))

        val result = repository().getCount()

        assertEquals(VehicleResult.Success(VehicleCount(count = 2, max = 3)), result)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/vehicles", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
        assertEquals("un GET no lleva cuerpo", 0, request.bodySize)
    }

    // --- Desvincular una --------------------------------------------------------------------

    @Test
    fun `desvincular publica en unlink el payload cifrado y devuelve el conteo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(count(0)))

        val result = repository().unlink(plate)

        assertEquals(VehicleResult.Success(VehicleCount(count = 0, max = 3)), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/vehicles/unlink", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
        val payload = decryptPayload(request)
        assertEquals(setOf("patente"), payload.keys().asSequence().toSet())
        assertEquals(plate, String(Base64.getDecoder().decode(payload.getString("patente"))))
    }

    // --- Desvincular todos ------------------------------------------------------------------

    @Test
    fun `desvincular todos hace DELETE a vehicles y 204 es exito`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository().unlinkAll()

        assertEquals(VehicleResult.Success(Unit), result)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/vehicles", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
        assertEquals("no lleva cuerpo ni patentes", 0, request.bodySize)
    }

    // --- Errores por code ---------------------------------------------------------------------

    @Test
    fun `cada code del backend tiene su error de dominio, sin mirar el status`() = runTest {
        val esperados = mapOf(
            "INVALID_PLATE" to VehicleError.InvalidPlate,
            "VEHICLE_NOT_FOUND" to VehicleError.NotFound,
            "DUPLICATE_VEHICLE" to VehicleError.Duplicate,
            "VEHICLE_LIMIT_REACHED" to VehicleError.LimitReached,
            "DEPENDENCY_UNAVAILABLE" to VehicleError.Unavailable,
        )

        esperados.forEach { (code, expected) ->
            // El status no importa: se mapea por el code (los dos 409 son distintos por esto).
            server.enqueue(MockResponse().setResponseCode(409).setBody(error(code)))

            assertEquals(
                "mapeo incorrecto para $code",
                VehicleResult.Failed(expected),
                repository().link(plate),
            )
        }
    }

    @Test
    fun `los dos 409 se distinguen por code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody(error("DUPLICATE_VEHICLE")))
        server.enqueue(MockResponse().setResponseCode(409).setBody(error("VEHICLE_LIMIT_REACHED")))

        assertEquals(VehicleResult.Failed(VehicleError.Duplicate), repository().link(plate))
        assertEquals(VehicleResult.Failed(VehicleError.LimitReached), repository().link(plate))
    }

    @Test
    fun `404 VEHICLE_NOT_FOUND al desvincular es no encontrada`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody(error("VEHICLE_NOT_FOUND")))

        assertEquals(VehicleResult.Failed(VehicleError.NotFound), repository().unlink(plate))
    }

    @Test
    fun `los errores tambien se mapean al contar y al desvincular todos`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "5").setBody(error("DEPENDENCY_UNAVAILABLE")))
        server.enqueue(MockResponse().setResponseCode(503).setBody(error("DEPENDENCY_UNAVAILABLE")))

        assertEquals(VehicleResult.Failed(VehicleError.Unavailable), repository().getCount())
        assertEquals(VehicleResult.Failed(VehicleError.Unavailable), repository().unlinkAll())
    }

    @Test
    fun `un 502 o 504 de Kong sin cuerpo tambien es no disponible`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setResponseCode(504))

        assertEquals(VehicleResult.Failed(VehicleError.Unavailable), repository().getCount())
        assertEquals(VehicleResult.Failed(VehicleError.Unavailable), repository().getCount())
    }

    @Test
    fun `un code desconocido o un cuerpo ilegible es un fallo inesperado`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody(error("ALGO_NUEVO")))
        server.enqueue(MockResponse().setResponseCode(400).setBody("no es json"))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(401))

        repeat(4) {
            assertEquals(VehicleResult.Failed(VehicleError.Unexpected), repository().link(plate))
        }
    }

    // --- Fallas de transporte -----------------------------------------------------------------

    @Test
    fun `sin conexion es un error de red`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(VehicleResult.Failed(VehicleError.Network), repository().getCount())
    }

    @Test
    fun `una respuesta que no es json no se confunde con un error de red`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("no es json"))

        assertEquals(VehicleResult.Failed(VehicleError.Unexpected), repository().getCount())
    }

    @Test
    fun `circuito abierto es no disponible y no sale a la red`() = runTest {
        forceCircuitOpen = true

        assertEquals(VehicleResult.Failed(VehicleError.Unavailable), repository().link(plate))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `sin clave de vehicle no se hace la request`() = runTest {
        val result = repository(publicKey = "").link(plate)

        assertEquals(VehicleResult.Failed(VehicleError.Configuration), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `una clave de vehicle que no es base64 tambien es un error de configuracion en unlink`() = runTest {
        assertEquals(VehicleResult.Failed(VehicleError.Configuration), repository(publicKey = "no-es-base64!").unlink(plate))
    }

    @Test
    fun `sin sesion no se llama a la red`() = runTest {
        tokenStore.clear()

        assertEquals(VehicleResult.Failed(VehicleError.Unexpected), repository().getCount())
        assertEquals(VehicleResult.Failed(VehicleError.Unexpected), repository().unlinkAll())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `cancelar la request no se convierte en un error`() = runTest {
        // CancellationException hereda de RuntimeException: si se la captura junto al resto,
        // abandonar la pantalla termina mostrando "algo salió mal" en vez de cancelar en silencio.
        val cancelada = object : VehicleApi {
            override suspend fun getCount(authorization: String): Response<ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehicleCountDto> =
                throw CancellationException("pantalla abandonada")

            override suspend fun link(
                authorization: String,
                body: ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehicleRequestDto,
            ): Response<ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehicleCountDto> = throw UnsupportedOperationException()

            override suspend fun unlink(
                authorization: String,
                body: ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehicleRequestDto,
            ): Response<ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehicleCountDto> = throw UnsupportedOperationException()

            override suspend fun unlinkAll(authorization: String): Response<Unit> = throw UnsupportedOperationException()
        }
        val repository = repository(vehicleApi = cancelada)

        assertThrows(CancellationException::class.java) { runBlocking { repository.getCount() } }
    }

    @Test
    fun `los modelos del payload y el conteo no exponen datos al renderizarse`() {
        assertEquals(
            "VehiclePayloadDto(***)",
            ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehiclePayloadDto("QUIxMjNDRA==").toString(),
        )
        assertEquals(
            "VehicleRequestDto(***)",
            ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehicleRequestDto("cripto").toString(),
        )
        assertNull(VehicleCount(0, 3).takeIf { it.isFull })
        assertTrue(VehicleCount(3, 3).isFull)
    }
}
