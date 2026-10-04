package ar.felaufernandezbottaro.vinculovial.data.remote

import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation
import java.io.IOException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente de tiempo del breaker. Inyectable para que los tests avancen el reloj a mano en vez
 * de dormir (`Thread.sleep` hace los tests lentos y dependientes de la carga de la máquina).
 *
 * El de producción es monotónico (`System.nanoTime`), no la hora de pared: con
 * `System.currentTimeMillis` un cambio de hora del sistema -- el usuario o la red móvil
 * corrigen el reloj -- dejaba un circuito abierto por horas (reloj hacia atrás) o lo cerraba
 * antes de tiempo (hacia adelante).
 */
fun interface MonotonicClock {
    fun nowMs(): Long

    companion object {
        val SYSTEM = MonotonicClock { System.nanoTime() / NANOS_PER_MILLI }
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

/** Umbral, ventana y duración de apertura -- ver `CircuitBreakerInterceptor`, provisto desde `BuildConfig`. */
data class CircuitBreakerConfig(
    val failureThreshold: Int,
    val windowMs: Long,
    val openMs: Long,
)

/**
 * El circuito de [endpoint] está abierto: esta request no llegó a tocar la red.
 *
 * Se trata como un fallo transitorio del servidor -- misma UI que un 503, nunca como un
 * problema de conexión del propio dispositivo -- por eso cada repositorio la atrapa aparte
 * de un `IOException` genérico, antes de llegar a ese catch.
 */
class CircuitBreakerOpenException(endpoint: String) :
    IOException("Circuito abierto para '$endpoint': la request no se intentó contra la red.")

/**
 * Clave del circuito de una request: el método de Retrofit que la originó (interfaz
 * declarante + nombre + tipos de parámetros), no su ruta. Dos métodos con el mismo prefijo de
 * ruta (`POST /alerts` y `GET /alerts/inbox`) o incluso con la misma ruta y distinto verbo
 * (`POST` y `DELETE` sobre `/notifications/tokens`) son circuitos independientes.
 *
 * Una request sin el tag `Invocation` de Retrofit -- ninguna de las reales lo carece, ver
 * `CircuitBreakerInterceptorTest` -- cae en `verbo + ruta`, no en una clave fija compartida:
 * así dos requests no relacionadas nunca comparten circuito por no tener tag.
 */
internal fun endpointKeyOf(request: Request): String {
    val method = request.tag(Invocation::class.java)?.method()
    return if (method != null) endpointKeyOf(method) else "${request.method} ${request.url.encodedPath}"
}

internal fun endpointKeyOf(method: Method): String =
    "${method.declaringClass.name}#${method.name}(${method.parameterTypes.joinToString(",") { it.name }})"

/**
 * Abre el circuito por endpoint -- ver [endpointKeyOf] -- cuando ese endpoint encadena
 * demasiadas fallas, y corta en seco las siguientes requests de ese mismo endpoint sin tocar
 * la red. Va *antes* del interceptor de attestation en `NetworkModule`: con el circuito
 * abierto, tampoco se paga el costo de Play Integrity.
 *
 * Singleton en memoria, sin persistencia -- reinicia en cada arranque de proceso. Un estado
 * por endpoint, no uno global: un endpoint caído no debería abrir el circuito de los demás.
 * El mismo singleton se inyecta en los dos `OkHttpClient` de `NetworkModule`, así que el
 * estado de cada endpoint es efectivamente compartido entre ambos clientes.
 *
 * Cuentan como falla un `IOException` (timeout, conexión rechazada) y cualquier `5xx`. Un
 * `4xx` del upstream se registra como éxito del circuito -- es evidencia de un problema con
 * *esta* request, no de que el servicio esté caído. Un fallo de attestation tampoco cuenta:
 * la request nunca salió del dispositivo, así que no es evidencia sobre el estado del
 * upstream.
 */
@Singleton
class CircuitBreakerInterceptor internal constructor(
    private val config: CircuitBreakerConfig,
    private val clock: MonotonicClock,
) : Interceptor {

    /** El constructor de producción; el de arriba existe para inyectar un reloj en los tests. */
    @Inject
    constructor(config: CircuitBreakerConfig) : this(config, MonotonicClock.SYSTEM)

    private val states = ConcurrentHashMap<String, CircuitState>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val endpoint = endpointKeyOf(request)
        val state = states.getOrPut(endpoint) { CircuitState() }
        val now = clock.nowMs()

        if (!state.tryAcquire(now, config.openMs)) {
            throw CircuitBreakerOpenException(endpoint)
        }

        return try {
            val response = chain.proceed(request)
            if (response.code in HTTP_SERVER_ERROR_RANGE) {
                state.recordFailure(now, config.failureThreshold, config.windowMs)
            } else {
                state.recordSuccess()
            }
            response
        } catch (e: AttestationUnavailableException) {
            // Nunca salió del dispositivo: no es evidencia sobre el upstream. Ver el `catch`
            // de abajo -- sin este, al heredar de IOException, contaría como una falla real.
            throw e
        } catch (e: IOException) {
            state.recordFailure(now, config.failureThreshold, config.windowMs)
            throw e
        }
    }

    private companion object {
        val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}

/**
 * Estado de un endpoint. Closed (sin fallas recientes) -> Open (tras [CircuitBreakerConfig.
 * failureThreshold] fallas dentro de [CircuitBreakerConfig.windowMs]) -> tras
 * [CircuitBreakerConfig.openMs], semiabierto: deja pasar exactamente una request de prueba;
 * si esa prueba falla, reabre de inmediato con un nuevo período de apertura; si tiene éxito,
 * cierra el circuito del todo.
 *
 * `synchronized` en vez de un `Mutex` de corrutinas a propósito: `Interceptor.intercept` es
 * una función bloqueante común, corre en el pool de hilos de OkHttp -- no hay nada para
 * suspender acá, y envolver esto en `runBlocking` sólo para tomar un lock sería ceremonia sin
 * beneficio.
 */
private class CircuitState {
    private val lock = Any()
    private var failureCount = 0
    private var windowStartedAt = 0L

    /** 0L = circuito cerrado. No-cero = abierto o, si ya pasó `openMs`, en prueba. */
    private var openedAt = 0L
    private var trialInFlight = false

    /** @return `false` si el circuito está abierto y no corresponde dejar pasar esta request. */
    fun tryAcquire(now: Long, openMs: Long): Boolean = synchronized(lock) {
        if (openedAt == 0L) return true // closed

        val stillOpen = now - openedAt < openMs
        when {
            stillOpen -> false
            trialInFlight -> false // semiabierto, pero ya hay una prueba en vuelo
            else -> {
                trialInFlight = true
                true
            }
        }
    }

    fun recordSuccess() = synchronized(lock) {
        failureCount = 0
        windowStartedAt = 0L
        openedAt = 0L
        trialInFlight = false
    }

    fun recordFailure(now: Long, threshold: Int, windowMs: Long) = synchronized(lock) {
        if (trialInFlight) {
            // La prueba del semiabierto falló: reabre ya, con un período de apertura nuevo --
            // no hace falta volver a acumular `threshold` fallas para esto.
            trialInFlight = false
            openedAt = now
            failureCount = 0
            windowStartedAt = 0L
            return
        }

        if (windowStartedAt == 0L || now - windowStartedAt > windowMs) {
            windowStartedAt = now
            failureCount = 1
        } else {
            failureCount++
        }

        if (failureCount >= threshold) {
            openedAt = now
        }
    }
}
