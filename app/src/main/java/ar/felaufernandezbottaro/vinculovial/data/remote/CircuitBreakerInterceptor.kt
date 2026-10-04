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
 * *esta* request, no de que el servicio esté caído.
 *
 * No cuentan, porque no son evidencia sobre el upstream, y **liberan el cupo de prueba** si
 * la request lo tenía (si no, el circuito quedaría rechazando todo para siempre):
 * - un fallo de attestation: la request nunca salió del dispositivo;
 * - una request cancelada por el llamador (el `IOException("Canceled")` de OkHttp);
 * - cualquier error que no sea de E/S, p. ej. el bug de otro interceptor.
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

        val permit = state.acquire(clock.nowMs(), config.openMs)
        if (permit == Permit.REJECTED) throw CircuitBreakerOpenException(endpoint)
        val isTrial = permit == Permit.TRIAL

        return try {
            val response = chain.proceed(request)
            // El instante del resultado es cuando TERMINÓ la request, no cuando empezó: una
            // prueba que tarda decenas de segundos en fallar no puede "reabrir" con un
            // período de apertura que ya venció mientras esperaba.
            val finishedAt = clock.nowMs()
            if (response.code in HTTP_SERVER_ERROR_RANGE) {
                state.recordFailure(finishedAt, config.failureThreshold, config.windowMs, isTrial)
            } else {
                state.recordSuccess(isTrial)
            }
            response
        } catch (e: AttestationUnavailableException) {
            // Nunca salió del dispositivo. Va antes que `IOException`: hereda de ella.
            state.releaseTrial(isTrial)
            throw e
        } catch (e: IOException) {
            if (chain.call().isCanceled()) {
                state.releaseTrial(isTrial)
            } else {
                state.recordFailure(clock.nowMs(), config.failureThreshold, config.windowMs, isTrial)
            }
            throw e
        } catch (e: Throwable) {
            state.releaseTrial(isTrial)
            throw e
        }
    }

    private companion object {
        val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}

private enum class Permit {
    /** Circuito cerrado: la request pasa como cualquier otra. */
    CLOSED,

    /** Semiabierto: ésta es LA request de prueba. Su resultado decide si cierra o reabre. */
    TRIAL,

    /** Abierto, o semiabierto con una prueba ya en vuelo: no se toca la red. */
    REJECTED,
}

/**
 * Estado de un endpoint. Cerrado (sin fallas recientes) -> abierto (tras
 * [CircuitBreakerConfig.failureThreshold] fallas dentro de [CircuitBreakerConfig.windowMs]) ->
 * tras [CircuitBreakerConfig.openMs], semiabierto: deja pasar exactamente una request de
 * prueba; si esa prueba falla, reabre de inmediato con un nuevo período de apertura; si tiene
 * éxito, cierra el circuito del todo.
 *
 * Sólo la request que tiene el permiso `TRIAL` resuelve el semiabierto. El resultado tardío de
 * una request que empezó antes de que el circuito abriera no es evidencia sobre la
 * recuperación: no lo cierra ni lo reabre.
 *
 * Los estados se llevan con banderas explícitas, no con un `0L` centinela: `MonotonicClock`
 * puede devolver 0 -- o un valor negativo, `System.nanoTime` tiene un origen arbitrario -- y
 * eso no puede confundirse con "cerrado" ni con "sin ventana en curso".
 *
 * `synchronized` en vez de un `Mutex` de corrutinas a propósito: `Interceptor.intercept` es
 * una función bloqueante común, corre en el pool de hilos de OkHttp -- no hay nada para
 * suspender acá, y envolver esto en `runBlocking` sólo para tomar un lock sería ceremonia sin
 * beneficio.
 */
private class CircuitState {
    private val lock = Any()

    private var inWindow = false
    private var windowStartedAt = 0L
    private var failureCount = 0

    private var isOpen = false
    private var openedAt = 0L
    private var trialInFlight = false

    fun acquire(now: Long, openMs: Long): Permit = synchronized(lock) {
        when {
            !isOpen -> Permit.CLOSED
            now - openedAt < openMs -> Permit.REJECTED
            trialInFlight -> Permit.REJECTED
            else -> {
                trialInFlight = true
                Permit.TRIAL
            }
        }
    }

    fun recordSuccess(isTrial: Boolean) = synchronized(lock) {
        when {
            isTrial -> closeCircuit()
            !isOpen -> clearWindow() // un éxito corta la racha de fallas
            else -> Unit // éxito tardío de una request anterior a la apertura: sin información
        }
    }

    fun recordFailure(now: Long, threshold: Int, windowMs: Long, isTrial: Boolean) = synchronized(lock) {
        when {
            // La prueba falló: reabre ya, con un período de apertura nuevo; no hace falta
            // volver a acumular `threshold` fallas.
            isTrial -> openCircuit(now)
            isOpen -> Unit // falla tardía de una request anterior a la apertura: ya está abierto
            else -> {
                if (!inWindow || now - windowStartedAt > windowMs) {
                    inWindow = true
                    windowStartedAt = now
                    failureCount = 1
                } else {
                    failureCount++
                }
                if (failureCount >= threshold) openCircuit(now)
            }
        }
    }

    /** La prueba terminó sin evidencia (attestation, cancelación, error ajeno): otra puede intentar. */
    fun releaseTrial(isTrial: Boolean) = synchronized(lock) {
        if (isTrial) trialInFlight = false
    }

    private fun openCircuit(now: Long) {
        isOpen = true
        openedAt = now
        trialInFlight = false
        clearWindow()
    }

    private fun closeCircuit() {
        isOpen = false
        trialInFlight = false
        clearWindow()
    }

    private fun clearWindow() {
        inWindow = false
        windowStartedAt = 0L
        failureCount = 0
    }
}
