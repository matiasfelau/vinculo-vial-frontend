package ar.felaufernandezbottaro.vinculovial.data.benefits

import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSessionKeyVault
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.benefits.Benefit
import ar.felaufernandezbottaro.vinculovial.domain.benefits.BenefitError
import ar.felaufernandezbottaro.vinculovial.domain.benefits.ClaimResult
import ar.felaufernandezbottaro.vinculovial.domain.benefits.GetBenefitsResult
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant

/**
 * Lista, banner con `ETag` y `claim` de US-18, contra un Gateway simulado: la forma de cada request
 * (Bearer, `If-None-Match`, ruta armada desde el `id`) y el mapeo de cada respuesta a un resultado
 * de dominio, por el `code` del backend y no por el status.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BenefitsRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenStore: SecureTokenStore
    private var apiInterceptor: Interceptor? = null

    private val id = "3f2b8c1e-0000-4000-8000-000000000001"
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokenStore = SecureTokenStore(FakeSharedPreferences(), FakeSessionKeyVault())
        tokenStore.startSession(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repository(): BenefitsRepositoryImpl {
        val moshi = Moshi.Builder().build()
        val client = OkHttpClient.Builder().apply { apiInterceptor?.let { addInterceptor(it) } }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return BenefitsRepositoryImpl(
            api = retrofit.create(BenefitsApi::class.java),
            tokenStore = tokenStore,
            dispatcher = UnconfinedTestDispatcher(),
            moshi = moshi,
        )
    }

    private fun json(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun benefitJson(
        id: String = this.id,
        endsAt: String = "2030-11-30T23:59:00-03:00",
        code: String? = null,
    ) = """{"id":"$id","partner_name":"Estación de prueba","title":"20 % en combustible","description":"Descripción","terms":"Condiciones","open_url":"https://partner.example/app","ends_at":"$endsAt","banner_url":"/api/v1/benefits/$id/banner","code":${if (code == null) "null" else "\"$code\""}}"""

    // -- Lista --------------------------------------------------------------------------------

    @Test
    fun `la lista se pide con el access token como bearer`() = runTest {
        server.enqueue(json(200, "[]"))

        repository().getBenefits()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/benefits", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
    }

    @Test
    fun `la lista se mapea al dominio, con el vencimiento como instante y el codigo nulo si no lo pidio`() = runTest {
        server.enqueue(json(200, "[${benefitJson()}]"))

        val result = repository().getBenefits() as GetBenefitsResult.Success

        assertEquals(
            listOf(
                Benefit(
                    id = id,
                    partnerName = "Estación de prueba",
                    title = "20 % en combustible",
                    description = "Descripción",
                    terms = "Condiciones",
                    openUrl = "https://partner.example/app",
                    endsAt = Instant.parse("2030-12-01T02:59:00Z"),
                    code = null,
                ),
            ),
            result.benefits,
        )
    }

    @Test
    fun `si el usuario ya pidio el codigo la lista lo trae`() = runTest {
        server.enqueue(json(200, "[${benefitJson(code = "ABCD-1234")}]"))

        val result = repository().getBenefits() as GetBenefitsResult.Success

        assertEquals("ABCD-1234", result.benefits.single().code)
    }

    @Test
    fun `la lista conserva el orden del servidor`() = runTest {
        server.enqueue(json(200, "[${benefitJson(id = "b")},${benefitJson(id = "a")}]"))

        val result = repository().getBenefits() as GetBenefitsResult.Success

        assertEquals(listOf("b", "a"), result.benefits.map { it.id })
    }

    @Test
    fun `una promocion con un vencimiento ilegible se descarta sin tumbar a las demas`() = runTest {
        server.enqueue(json(200, "[${benefitJson(id = "mala", endsAt = "manana")},${benefitJson(id = "buena")}]"))

        val result = repository().getBenefits() as GetBenefitsResult.Success

        assertEquals(listOf("buena"), result.benefits.map { it.id })
    }

    @Test
    fun `una lista vacia es un exito vacio`() = runTest {
        server.enqueue(json(200, "[]"))

        assertEquals(GetBenefitsResult.Success(emptyList()), repository().getBenefits())
    }

    @Test
    fun `cualquier falla de la lista es Failed`() = runTest {
        listOf(401, 429, 500, 502, 503).forEach { status ->
            server.enqueue(json(status, """{"error":"x","code":"DEPENDENCY_UNAVAILABLE"}"""))

            assertEquals("status $status", GetBenefitsResult.Failed, repository().getBenefits())
        }
    }

    @Test
    fun `un cuerpo que no es una lista es Failed`() = runTest {
        server.enqueue(json(200, "no es json"))

        assertEquals(GetBenefitsResult.Failed, repository().getBenefits())
    }

    @Test
    fun `un corte de red al pedir la lista es Failed`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(GetBenefitsResult.Failed, repository().getBenefits())
    }

    @Test
    fun `sin sesion guardada no llama a la red`() = runTest {
        tokenStore.clear()

        assertEquals(GetBenefitsResult.Failed, repository().getBenefits())
        assertNull(repository().getBanner(id))
        assertEquals(ClaimResult.Failed(BenefitError.Unexpected), repository().claim(id))
        assertEquals(0, server.requestCount)
    }

    // -- Banner -------------------------------------------------------------------------------

    private fun bannerResponse(etag: String? = "\"abc123\"") = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "image/png")
        .setHeader("Cache-Control", "private, max-age=3600")
        .apply { if (etag != null) setHeader("ETag", etag) }
        .setBody(Buffer().write(png))

    @Test
    fun `el banner se pide por el id, con el bearer, y llega como bytes`() = runTest {
        server.enqueue(bannerResponse())

        val bytes = repository().getBanner(id)

        val request = server.takeRequest()
        assertEquals("/api/v1/benefits/$id/banner", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
        assertEquals(null, request.getHeader("If-None-Match"))
        assertArrayEquals(png, bytes)
    }

    @Test
    fun `la segunda vez el banner se pide con If-None-Match y un 304 devuelve los bytes guardados`() = runTest {
        server.enqueue(bannerResponse(etag = "\"abc123\""))
        server.enqueue(MockResponse().setResponseCode(304))
        val repository = repository()

        val first = repository.getBanner(id)
        server.takeRequest()
        val second = repository.getBanner(id)

        assertEquals("\"abc123\"", server.takeRequest().getHeader("If-None-Match"))
        assertArrayEquals(png, first)
        assertArrayEquals(png, second)
    }

    @Test
    fun `si el banner cambio el servidor manda uno nuevo y se guarda su ETag`() = runTest {
        val newPng = byteArrayOf(9, 9, 9, 9)
        server.enqueue(bannerResponse(etag = "\"v1\""))
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"v2\"").setBody(Buffer().write(newPng)))
        server.enqueue(MockResponse().setResponseCode(304))
        val repository = repository()

        repository.getBanner(id)
        val changed = repository.getBanner(id)
        val cached = repository.getBanner(id)

        assertArrayEquals(newPng, changed)
        assertArrayEquals(newPng, cached)
        server.takeRequest()
        server.takeRequest()
        assertEquals("\"v2\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `cada banner tiene su propia cache`() = runTest {
        server.enqueue(bannerResponse(etag = "\"uno\""))
        server.enqueue(bannerResponse(etag = "\"dos\""))
        val repository = repository()

        repository.getBanner("uno")
        repository.getBanner("dos")

        server.takeRequest()
        assertEquals(null, server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `sin ETag no hay cache y la proxima vez se pide entero`() = runTest {
        server.enqueue(bannerResponse(etag = null))
        server.enqueue(bannerResponse(etag = null))
        val repository = repository()

        repository.getBanner(id)
        repository.getBanner(id)

        server.takeRequest()
        assertEquals(null, server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `un 304 sin nada guardado es null, no un banner vacio`() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))

        assertNull(repository().getBanner(id))
    }

    @Test
    fun `un banner que falla es null, nunca un error`() = runTest {
        listOf(404, 429, 500, 503).forEach { status ->
            server.enqueue(json(status, """{"error":"x","code":"BENEFIT_NOT_FOUND"}"""))

            assertNull("status $status", repository().getBanner(id))
        }
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertNull(repository().getBanner(id))
    }

    @Test
    fun `un cuerpo vacio o demasiado grande no es un banner`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"x\""))
        assertNull(repository().getBanner(id))

        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("ETag", "\"y\"")
                .setBody(Buffer().write(ByteArray(1_048_577))),
        )
        assertNull(repository().getBanner(id))
    }

    // -- Claim --------------------------------------------------------------------------------

    @Test
    fun `claim es un POST por el id, con el bearer, sin body, y devuelve el codigo`() = runTest {
        server.enqueue(json(200, """{"code":"ABCD-1234"}"""))

        val result = repository().claim(id)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/benefits/$id/claim", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
        assertEquals(0L, request.bodySize)
        assertEquals(ClaimResult.Success("ABCD-1234"), result)
    }

    @Test
    fun `404 BENEFIT_NOT_FOUND es NotFound`() = runTest {
        server.enqueue(json(404, """{"error":"x","code":"BENEFIT_NOT_FOUND"}"""))

        assertEquals(ClaimResult.Failed(BenefitError.NotFound), repository().claim(id))
    }

    @Test
    fun `409 NO_CODES_LEFT es NoCodesLeft`() = runTest {
        server.enqueue(json(409, """{"error":"x","code":"NO_CODES_LEFT"}"""))

        assertEquals(ClaimResult.Failed(BenefitError.NoCodesLeft), repository().claim(id))
    }

    @Test
    fun `se mapea por el code y no por el status`() = runTest {
        server.enqueue(json(404, """{"error":"x","code":"OTRO"}"""))
        assertEquals(ClaimResult.Failed(BenefitError.Unexpected), repository().claim(id))

        server.enqueue(json(409, """{"error":"x","code":"OTRO"}"""))
        assertEquals(ClaimResult.Failed(BenefitError.Unexpected), repository().claim(id))

        server.enqueue(json(404, "no es json"))
        assertEquals(ClaimResult.Failed(BenefitError.Unexpected), repository().claim(id))
    }

    @Test
    fun `429 es RateLimited, aunque no traiga code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        assertEquals(ClaimResult.Failed(BenefitError.RateLimited), repository().claim(id))
    }

    @Test
    fun `502, 503 y 504 son Unavailable`() = runTest {
        listOf(502, 503, 504).forEach { status ->
            server.enqueue(json(status, """{"error":"x","code":"DEPENDENCY_UNAVAILABLE"}"""))

            assertEquals("status $status", ClaimResult.Failed(BenefitError.Unavailable), repository().claim(id))
        }
    }

    @Test
    fun `500 y 401 son un error inesperado`() = runTest {
        listOf(500, 401).forEach { status ->
            server.enqueue(MockResponse().setResponseCode(status))

            assertEquals("status $status", ClaimResult.Failed(BenefitError.Unexpected), repository().claim(id))
        }
    }

    @Test
    fun `un 200 sin codigo es un error inesperado`() = runTest {
        server.enqueue(json(200, """{"code":""}"""))

        assertEquals(ClaimResult.Failed(BenefitError.Unexpected), repository().claim(id))
    }

    @Test
    fun `un corte de red es Network`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(ClaimResult.Failed(BenefitError.Network), repository().claim(id))
    }

    @Test
    fun `circuito abierto es Unavailable, no Network`() = runTest {
        apiInterceptor = Interceptor { throw CircuitBreakerOpenException("benefits") }

        assertEquals(ClaimResult.Failed(BenefitError.Unavailable), repository().claim(id))
    }

    @Test
    fun `un cuerpo que no se puede leer es un error inesperado`() = runTest {
        server.enqueue(json(200, """{"code":{"anidado":1}}"""))

        assertEquals(ClaimResult.Failed(BenefitError.Unexpected), repository().claim(id))
    }

    // -- Privacidad ---------------------------------------------------------------------------

    @Test
    fun `el codigo no aparece en el toString de ningun objeto que lo lleva`() = runTest {
        server.enqueue(json(200, "[${benefitJson(code = "SECRETO-9999")}]"))
        val benefit = (repository().getBenefits() as GetBenefitsResult.Success).benefits.single()

        assertFalse("SECRETO-9999" in benefit.toString())
        assertFalse("SECRETO-9999" in ClaimResult.Success("SECRETO-9999").toString())
        assertTrue("***" in benefit.toString())
    }
}
