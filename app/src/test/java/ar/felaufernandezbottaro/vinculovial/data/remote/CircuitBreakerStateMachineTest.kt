package ar.felaufernandezbottaro.vinculovial.data.remote

import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * La máquina de estados del breaker, sin red real ni tiempo real: el upstream es un
 * interceptor con respuestas programadas y el reloj lo avanza el propio test. Nada duerme --
 * los únicos `await` con timeout son guardas contra un cuelgue del test, no esperas.
 *
 * El cableado con Retrofit y OkHttp reales (clave por endpoint, errores de conexión) está en
 * `CircuitBreakerInterceptorTest`.
 */
class CircuitBreakerStateMachineTest {

    /** Arranca en 1000, no en 0: un reloj en 0 es un caso aparte, con su propio test abajo. */
    private class FakeClock(private var current: Long = 1_000L) : MonotonicClock {
        override fun nowMs() = current
        fun advance(ms: Long) { current += ms }
        fun set(ms: Long) { current = ms }
    }

    /** El upstream: cada llamada que llega al final de la cadena consume una acción programada. */
    private class ScriptedUpstream : Interceptor {
        private val actions = ArrayDeque<(Interceptor.Chain) -> Response>()
        var calls = 0
            private set

        override fun intercept(chain: Interceptor.Chain): Response {
            calls++
            val action = actions.removeFirstOrNull() ?: error("llegó una request sin acción programada")
            return action(chain)
        }

        fun respond(code: Int) = actions.addLast { chain -> response(chain.request(), code) }
        fun fail(error: IOException) = actions.addLast { throw error }
        fun run(action: (Interceptor.Chain) -> Response) = actions.addLast(action)
    }

    private class Harness(config: CircuitBreakerConfig) {
        val clock = FakeClock()
        val upstream = ScriptedUpstream()
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(CircuitBreakerInterceptor(config, clock))
            .addInterceptor(upstream)
            .build()

        fun request(path: String = "/api/v1/alerts") = Request.Builder().url("http://localhost$path").build()

        /** `null` si la request completó, la excepción si no. */
        fun call(path: String = "/api/v1/alerts"): IOException? = try {
            client.newCall(request(path)).execute().close()
            null
        } catch (e: IOException) {
            e
        }
    }

    private companion object {
        fun response(request: Request, code: Int): Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("programada")
            .body("".toResponseBody(null))
            .build()

        fun config(threshold: Int = 2, windowMs: Long = 60_000, openMs: Long = 30_000) =
            CircuitBreakerConfig(failureThreshold = threshold, windowMs = windowMs, openMs = openMs)

        fun isOpenRejection(e: IOException?) = e is CircuitBreakerOpenException
    }

    // -- Qué cuenta como falla ------------------------------------------------------------

    @Test
    fun `4xx repetidos no abren el circuito`() {
        val h = Harness(config(threshold = 2))
        repeat(5) { h.upstream.respond(400) }

        repeat(5) { assertNull(h.call()) }

        assertEquals(5, h.upstream.calls)
    }

    @Test
    fun `N fallas 5xx abren el circuito -- la N+1 no toca la red`() {
        val h = Harness(config(threshold = 2))
        h.upstream.respond(503)
        h.upstream.respond(503)
        h.call()
        h.call()
        assertEquals(2, h.upstream.calls)

        assertTrue(isOpenRejection(h.call()))
        assertEquals(2, h.upstream.calls) // la 3ra no llegó al upstream
    }

    @Test
    fun `un IOException cuenta como falla igual que un 5xx`() {
        val h = Harness(config(threshold = 1))
        h.upstream.fail(IOException("timeout"))

        val failure = h.call()
        assertTrue(failure != null && !isOpenRejection(failure)) // la falla en sí, no un rechazo

        assertTrue(isOpenRejection(h.call())) // y con threshold=1 ya abrió
    }

    @Test
    fun `un fallo de attestation no cuenta como falla del circuito`() {
        val h = Harness(config(threshold = 1))
        repeat(6) {
            h.upstream.fail(AttestationUnavailableException("no disponible", AttestationUnavailableException.Reason.TRANSIENT))
        }

        // Si contara, con threshold=1 el primero ya abriría y el resto serían rechazos.
        repeat(6) { assertTrue(h.call() is AttestationUnavailableException) }
        assertEquals(6, h.upstream.calls)
    }

    // -- Ventana de fallas ----------------------------------------------------------------

    @Test
    fun `las fallas fuera de la ventana no se acumulan`() {
        val h = Harness(config(threshold = 2, windowMs = 1_000))
        h.upstream.respond(503)
        h.call()
        h.clock.advance(1_001) // la primera falla ya salió de la ventana

        h.upstream.respond(503)
        h.call()
        h.upstream.respond(200)

        assertNull(h.call()) // sigue cerrado: una sola falla dentro de la ventana
    }

    @Test
    fun `dos fallas dentro de la ventana abren el circuito`() {
        val h = Harness(config(threshold = 2, windowMs = 1_000))
        h.upstream.respond(503)
        h.call()
        h.clock.advance(999)
        h.upstream.respond(503)
        h.call()

        assertTrue(isOpenRejection(h.call()))
    }

    @Test
    fun `un exito corta la racha de fallas`() {
        val h = Harness(config(threshold = 2))
        h.upstream.respond(503)
        h.upstream.respond(200)
        h.upstream.respond(503)
        h.upstream.respond(200)

        h.call()
        h.call()
        h.call()

        assertNull(h.call()) // 503, 200, 503: nunca dos fallas seguidas
    }

    // -- Semiabierto ----------------------------------------------------------------------

    private fun Harness.openAndWaitForHalfOpen(openMs: Long = 30_000) {
        upstream.respond(503)
        call()
        clock.advance(openMs)
    }

    @Test
    fun `mientras dura la apertura rechaza sin tocar la red`() {
        val h = Harness(config(threshold = 1, openMs = 30_000))
        h.upstream.respond(503)
        h.call()
        h.clock.advance(29_999)

        assertTrue(isOpenRejection(h.call()))
        assertEquals(1, h.upstream.calls)
    }

    @Test
    fun `cumplido el periodo de apertura una prueba exitosa cierra el circuito`() {
        val h = Harness(config(threshold = 1))
        h.openAndWaitForHalfOpen()

        h.upstream.respond(200)
        assertNull(h.call()) // la prueba

        h.upstream.respond(200)
        assertNull(h.call()) // ya cerrado: pasa normal
        assertEquals(3, h.upstream.calls)
    }

    @Test
    fun `si la prueba falla reabre con un periodo de apertura nuevo`() {
        val h = Harness(config(threshold = 1, openMs = 30_000))
        h.openAndWaitForHalfOpen()
        h.upstream.respond(503)
        h.call() // la prueba falla

        h.clock.advance(29_999)
        assertTrue(isOpenRejection(h.call())) // sigue abierto: el período se cuenta de nuevo

        h.clock.advance(1)
        h.upstream.respond(200)
        assertNull(h.call()) // y a los 30 s hay otra prueba
    }

    @Test
    fun `en el semiabierto pasa una sola prueba a la vez`() {
        val h = Harness(config(threshold = 1))
        h.openAndWaitForHalfOpen()
        val trialStarted = CountDownLatch(1)
        val releaseTrial = CountDownLatch(1)
        h.upstream.run { chain ->
            trialStarted.countDown()
            releaseTrial.await(10, TimeUnit.SECONDS)
            response(chain.request(), 200)
        }

        val trial = thread { h.call() }
        assertTrue(trialStarted.await(10, TimeUnit.SECONDS))

        assertTrue(isOpenRejection(h.call())) // la prueba sigue en vuelo: ésta se rechaza

        releaseTrial.countDown()
        trial.join(10_000)
        h.upstream.respond(200)
        assertNull(h.call()) // la prueba cerró el circuito
    }

    // -- Pruebas que terminan sin evidencia sobre el upstream -----------------------------

    /**
     * Si la prueba del semiabierto termina por algo que no es culpa del upstream y el breaker
     * no libera el cupo de prueba, el circuito queda rechazando todo para siempre: ninguna
     * request vuelve a llegar a la red ni a attestation, hasta reiniciar el proceso.
     */
    @Test
    fun `un fallo de attestation durante la prueba no deja el circuito trabado`() {
        val h = Harness(config(threshold = 1))
        h.openAndWaitForHalfOpen()
        h.upstream.fail(AttestationUnavailableException("sin Play", AttestationUnavailableException.Reason.TRANSIENT))
        assertTrue(h.call() is AttestationUnavailableException) // la prueba no es evidencia

        h.upstream.respond(200)
        assertNull(h.call()) // se puede probar de nuevo
    }

    @Test
    fun `un error inesperado durante la prueba tampoco deja el circuito trabado`() {
        val h = Harness(config(threshold = 1))
        h.openAndWaitForHalfOpen()
        h.upstream.run { throw IllegalStateException("bug de otro interceptor") }
        try {
            h.call()
        } catch (e: IllegalStateException) {
            // esperado: el breaker no lo traga
        }

        h.upstream.respond(200)
        assertNull(h.call())
    }

    // -- Cancelación ----------------------------------------------------------------------

    /** Cancela la call en vuelo (como lo hace Retrofit al cancelarse la corrutina) y espera que termine. */
    private fun Harness.callCancelledInFlight(): IOException? {
        val inFlight = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        upstream.run {
            inFlight.countDown()
            cancelled.await(10, TimeUnit.SECONDS)
            throw IOException("Canceled")
        }
        val call: Call = client.newCall(request())
        var outcome: IOException? = null
        val worker = thread { outcome = try { call.execute().close(); null } catch (e: IOException) { e } }
        assertTrue(inFlight.await(10, TimeUnit.SECONDS))
        call.cancel()
        cancelled.countDown()
        worker.join(10_000)
        return outcome
    }

    @Test
    fun `una request cancelada por el usuario no cuenta como falla del upstream`() {
        val h = Harness(config(threshold = 1))

        assertTrue(h.callCancelledInFlight() != null)

        h.upstream.respond(200)
        assertNull(h.call()) // con threshold=1, si hubiera contado ya estaría abierto
    }

    @Test
    fun `una cancelacion durante la prueba libera el cupo en vez de reabrir el circuito`() {
        val h = Harness(config(threshold = 1))
        h.openAndWaitForHalfOpen()

        assertTrue(h.callCancelledInFlight() != null)

        h.upstream.respond(200)
        assertNull(h.call()) // sigue en semiabierto: hay otra prueba disponible
    }

    // -- Tiempo ---------------------------------------------------------------------------

    /**
     * Una prueba que tarda (timeouts de decenas de segundos) debe reabrir el circuito desde
     * que TERMINA, no desde que empezó: si no, para cuando falla ya pasó el período de
     * apertura y el circuito "reabre" sin cooldown.
     */
    @Test
    fun `la falla de una prueba lenta se fecha al terminar, no al empezar`() {
        val h = Harness(config(threshold = 1, openMs = 1_000))
        h.openAndWaitForHalfOpen(openMs = 1_000)
        h.upstream.run { chain ->
            h.clock.advance(5_000) // la prueba tarda 5 s en fallar
            response(chain.request(), 503)
        }
        h.call()

        assertTrue(isOpenRejection(h.call())) // recién falló: tiene que seguir abierto
    }

    @Test
    fun `un reloj que marca 0 no confunde al estado con cerrado`() {
        val h = Harness(config(threshold = 1))
        h.clock.set(0)
        h.upstream.respond(503)
        h.call() // falla con el reloj en 0

        assertTrue(isOpenRejection(h.call()))
    }

    @Test
    fun `un reloj negativo tambien funciona`() {
        val h = Harness(config(threshold = 2))
        h.clock.set(-5_000) // System.nanoTime() puede ser negativo: su origen es arbitrario
        h.upstream.respond(503)
        h.upstream.respond(503)
        h.call()
        h.call()

        assertTrue(isOpenRejection(h.call()))
    }

    // -- Independencia entre endpoints (sin tag: verbo + ruta) ----------------------------

    @Test
    fun `un endpoint abierto no bloquea a otro`() {
        val h = Harness(config(threshold = 1))
        h.upstream.respond(503)
        h.call("/api/v1/alerts")

        h.upstream.respond(200)
        assertNull(h.call("/api/v1/vehicles"))
        assertTrue(isOpenRejection(h.call("/api/v1/alerts")))
    }
}
