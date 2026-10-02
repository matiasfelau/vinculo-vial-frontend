package ar.felaufernandezbottaro.vinculovial.data.remote

import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * El estado del breaker en sí, contra un servidor real (MockWebServer) -- mismo patrón que
 * `AttestationInterceptorTest`. Que `CircuitBreakerOpenException` se mapee a `Unavailable` en
 * cada repositorio se cubre aparte, en cada `*RepositoryImplTest`, con un fake más simple:
 * acá sólo importa cuándo el circuito abre, rechaza y cierra.
 */
class CircuitBreakerInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun clientWith(config: CircuitBreakerConfig) = OkHttpClient.Builder()
        .addInterceptor(CircuitBreakerInterceptor(config))
        .build()

    private interface DummyApi {
        @retrofit2.http.POST("/api/v1/alerts")
        fun postAlert()

        @retrofit2.http.GET("/api/v1/alerts/inbox")
        fun getInbox()
    }

    private val postAlertMethod = DummyApi::class.java.getMethod("postAlert")
    private val getInboxMethod = DummyApi::class.java.getMethod("getInbox")

    private val postAlertInvocation = retrofit2.Invocation.of(postAlertMethod, emptyList<Any>())
    private val getInboxInvocation = retrofit2.Invocation.of(getInboxMethod, emptyList<Any>())

    private fun requestTo(path: String, invocation: retrofit2.Invocation?) = Request.Builder()
        .url(server.url(path))
        .post("{}".toRequestBody("application/json".toMediaType()))
        .apply {
            if (invocation != null) {
                tag(retrofit2.Invocation::class.java, invocation)
            }
        }
        .build()

    private fun OkHttpClient.callOrNull(
        path: String = "/api/v1/alerts",
        invocation: retrofit2.Invocation? = null
    ): IOException? = try {
        newCall(requestTo(path, invocation)).execute().close()
        null
    } catch (e: IOException) {
        e
    }

    @Test
    fun `4xx repetidos no abren el circuito`() {
        val client = clientWith(CircuitBreakerConfig(failureThreshold = 2, windowMs = 60_000, openMs = 30_000))
        repeat(5) { server.enqueue(MockResponse().setResponseCode(400)) }

        repeat(5) { client.callOrNull() }

        assertEquals(5, server.requestCount)
    }

    @Test
    fun `N fallas 5xx abren el circuito -- la N+1 no toca la red`() {
        val client = clientWith(CircuitBreakerConfig(failureThreshold = 2, windowMs = 60_000, openMs = 30_000))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))

        client.callOrNull()
        client.callOrNull()
        assertEquals(2, server.requestCount)

        val rejection = client.callOrNull()

        assertTrue(rejection is CircuitBreakerOpenException)
        assertEquals(2, server.requestCount) // la 3ra no incrementó el contador del server
    }

    @Test
    fun `un error de conexion cuenta como falla, igual que un 5xx`() {
        val client = clientWith(CircuitBreakerConfig(failureThreshold = 1, windowMs = 60_000, openMs = 30_000))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val firstFailure = client.callOrNull()
        assertTrue(firstFailure != null && firstFailure !is CircuitBreakerOpenException)

        val rejection = client.callOrNull()
        assertTrue(rejection is CircuitBreakerOpenException)
    }

    @Test
    fun `un fallo de attestation no cuenta como falla del circuito`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(CircuitBreakerInterceptor(CircuitBreakerConfig(failureThreshold = 1, windowMs = 60_000, openMs = 30_000)))
            .addInterceptor {
                throw AttestationUnavailableException("no disponible", AttestationUnavailableException.Reason.TRANSIENT)
            }
            .build()

        // Si contara, ya con threshold=1 el circuito abriría en el primer intento y el
        // segundo lanzaría CircuitBreakerOpenException en vez de AttestationUnavailableException.
        repeat(5) { client.callOrNull() }

        val lastException = client.callOrNull()
        assertTrue(lastException is AttestationUnavailableException)
    }

    @Test
    fun `tras el periodo de apertura deja pasar una prueba -- si funciona, cierra el circuito`() {
        val client = clientWith(CircuitBreakerConfig(failureThreshold = 1, windowMs = 60_000, openMs = 500))
        server.enqueue(MockResponse().setResponseCode(503)) // abre el circuito
        client.callOrNull()
        assertEquals(1, server.requestCount)

        assertTrue(client.callOrNull() is CircuitBreakerOpenException) // sigue abierto
        assertEquals(1, server.requestCount)

        Thread.sleep(600) // supera openMs = 500

        server.enqueue(MockResponse().setResponseCode(200)) // la prueba del semiabierto
        assertEquals(null, client.callOrNull())
        assertEquals(2, server.requestCount)

        // Cerrado del todo: ya no es "la única prueba", la siguiente pasa normal.
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(null, client.callOrNull())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `si la prueba del semiabierto falla, reabre de inmediato`() {
        val client = clientWith(CircuitBreakerConfig(failureThreshold = 1, windowMs = 60_000, openMs = 500))
        server.enqueue(MockResponse().setResponseCode(503)) // abre
        client.callOrNull()
        Thread.sleep(600)

        server.enqueue(MockResponse().setResponseCode(503)) // la prueba también falla
        client.callOrNull()
        assertEquals(2, server.requestCount)

        // Reabrió con un período nuevo: la siguiente no toca la red aunque ya pasaron
        // los 500ms originales.
        assertTrue(client.callOrNull() is CircuitBreakerOpenException)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `el circuito es independiente por endpoint -- abrir alerts no afecta vehicles`() {
        val client = clientWith(CircuitBreakerConfig(failureThreshold = 1, windowMs = 60_000, openMs = 30_000))
        server.enqueue(MockResponse().setResponseCode(503)) // abre el endpoint "/api/v1/alerts"
        client.callOrNull(path = "/api/v1/alerts")

        server.enqueue(MockResponse().setResponseCode(200))
        val vehiclesFailure = client.callOrNull(path = "/api/v1/vehicles")

        assertEquals(null, vehiclesFailure)
        assertEquals(2, server.requestCount) // las dos tocaron la red
    }

    @Test
    fun `N fallas en un endpoint no abren el circuito de otro -- POST alerts no bloquea GET inbox`() {
        val client = clientWith(CircuitBreakerConfig(failureThreshold = 1, windowMs = 60_000, openMs = 30_000))
        server.enqueue(MockResponse().setResponseCode(503)) // abre POST /api/v1/alerts
        client.callOrNull(path = "/api/v1/alerts", invocation = postAlertInvocation)

        server.enqueue(MockResponse().setResponseCode(200))
        val inboxFailure = client.callOrNull(path = "/api/v1/alerts/inbox", invocation = getInboxInvocation)

        assertEquals(null, inboxFailure)
        assertEquals(2, server.requestCount) // las dos tocaron la red
    }
}
