package ar.felaufernandezbottaro.vinculovial.data.remote

import ar.felaufernandezbottaro.vinculovial.data.alert.AlertApi
import ar.felaufernandezbottaro.vinculovial.data.alert.VehicleCheckApi
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException
import ar.felaufernandezbottaro.vinculovial.data.inbox.InboxApi
import ar.felaufernandezbottaro.vinculovial.data.notifications.NotificationsApi
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.RefreshApi
import ar.felaufernandezbottaro.vinculovial.data.vehicle.VehicleApi
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
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.HTTP
import retrofit2.http.OPTIONS
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
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

    private fun requestTo(path: String) = Request.Builder()
        .url(server.url(path))
        .post("{}".toRequestBody("application/json".toMediaType()))
        .build()

    private fun OkHttpClient.callOrNull(path: String = "/api/v1/alerts"): IOException? = try {
        newCall(requestTo(path)).execute().close()
        null
    } catch (e: IOException) {
        e
    }

    private fun fixtureApi(config: CircuitBreakerConfig): BreakerFixtureApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(clientWith(config))
        .build()
        .create(BreakerFixtureApi::class.java)

    private fun Call<Unit>.callOrNull(): IOException? = try {
        execute()
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
        val client = clientWith(CircuitBreakerConfig(failureThreshold = 1, windowMs = 60_000, openMs = 50))
        server.enqueue(MockResponse().setResponseCode(503)) // abre el circuito
        client.callOrNull()
        assertEquals(1, server.requestCount)

        assertTrue(client.callOrNull() is CircuitBreakerOpenException) // sigue abierto
        assertEquals(1, server.requestCount)

        Thread.sleep(80) // supera openMs = 50

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
        val client = clientWith(CircuitBreakerConfig(failureThreshold = 1, windowMs = 60_000, openMs = 50))
        server.enqueue(MockResponse().setResponseCode(503)) // abre
        client.callOrNull()
        Thread.sleep(80)

        server.enqueue(MockResponse().setResponseCode(503)) // la prueba también falla
        client.callOrNull()
        assertEquals(2, server.requestCount)

        // Reabrió con un período nuevo: la siguiente no toca la red aunque ya pasaron
        // los 50ms originales.
        assertTrue(client.callOrNull() is CircuitBreakerOpenException)
        assertEquals(2, server.requestCount)
    }

    // -- Clave por endpoint (método de Retrofit), no por ruta ----------------------------

    private val oneFailureOpens = CircuitBreakerConfig(failureThreshold = 1, windowMs = 60_000, openMs = 30_000)

    @Test
    fun `POST alerts abierto no bloquea GET inbox -- mismo prefijo de ruta`() {
        val api = fixtureApi(oneFailureOpens)
        server.enqueue(MockResponse().setResponseCode(503)) // abre postAlert
        api.postAlert().callOrNull()
        assertTrue(api.postAlert().callOrNull() is CircuitBreakerOpenException)

        server.enqueue(MockResponse().setResponseCode(200))
        val inbox = api.getInbox().callOrNull()

        assertEquals(null, inbox)
        assertEquals(2, server.requestCount) // postAlert (503) + getInbox; el 2do postAlert no tocó la red
    }

    /** El caso que una clave por ruta (o por prefijo) confunde: misma URL, distinto verbo. */
    @Test
    fun `misma ruta con distinto verbo son circuitos separados -- POST y DELETE de tokens`() {
        val api = fixtureApi(oneFailureOpens)
        server.enqueue(MockResponse().setResponseCode(503)) // abre registerToken
        api.registerToken().callOrNull()

        server.enqueue(MockResponse().setResponseCode(200))
        val delete = api.deleteToken().callOrNull()

        assertEquals(null, delete)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `el circuito de un endpoint rechaza su propia siguiente llamada sin tocar la red`() {
        val api = fixtureApi(oneFailureOpens)
        server.enqueue(MockResponse().setResponseCode(503))
        api.getInbox().callOrNull()

        val rejection = api.getInbox().callOrNull()

        assertTrue(rejection is CircuitBreakerOpenException)
        assertEquals(1, server.requestCount)
    }

    /** Sin tag de Retrofit el fallback es verbo + ruta: tampoco hay una clave fija compartida. */
    @Test
    fun `sin tag de Retrofit, dos rutas distintas no comparten circuito`() {
        val client = clientWith(oneFailureOpens)
        server.enqueue(MockResponse().setResponseCode(503)) // abre "POST /api/v1/alerts"
        client.callOrNull(path = "/api/v1/alerts")

        server.enqueue(MockResponse().setResponseCode(200))
        val vehicles = client.callOrNull(path = "/api/v1/vehicles")

        assertEquals(null, vehicles)
        assertEquals(2, server.requestCount)
    }

    // -- Guarda sobre la API real: ninguna request real cae sin tag ni comparte clave --------

    private val realApis = listOf(
        AuthApiService::class.java,
        RefreshApi::class.java,
        VehicleApi::class.java,
        VehicleCheckApi::class.java,
        AlertApi::class.java,
        InboxApi::class.java,
        NotificationsApi::class.java,
    )

    private val retrofitHttpAnnotations = setOf<Class<out Annotation>>(
        GET::class.java,
        POST::class.java,
        PUT::class.java,
        PATCH::class.java,
        DELETE::class.java,
        HEAD::class.java,
        OPTIONS::class.java,
        HTTP::class.java,
    )

    /**
     * Retrofit adjunta el tag `Invocation` a toda request que sale de un método con anotación
     * HTTP, y el código de producción no arma requests a mano (sólo reescribe las existentes
     * con `newBuilder()`, que conserva los tags): si cada método real es un endpoint anotado,
     * ninguna request real cae en el fallback. Si se agrega una interfaz nueva y no se suma
     * acá, el conteo falla y obliga a revisarla.
     */
    @Test
    fun `cada metodo de las APIs reales es un endpoint de Retrofit con clave propia`() {
        val methods = realApis.flatMap { it.declaredMethods.toList() }

        val notEndpoints = methods.filter { m -> m.annotations.none { it.annotationClass.java in retrofitHttpAnnotations } }
        assertTrue("métodos sin anotación HTTP de Retrofit: $notEndpoints", notEndpoints.isEmpty())

        assertEquals(10, methods.size)
        assertEquals(10, methods.map { endpointKeyOf(it) }.toSet().size)
    }
}
