package ar.felaufernandezbottaro.vinculovial.data.remote

import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Umbral, ventana y duración de apertura -- ver `CircuitBreakerInterceptor`, provisto desde `BuildConfig`. */
data class CircuitBreakerConfig(
    val failureThreshold: Int,
    val windowMs: Long,
    val openMs: Long,
)

/**
 * El circuito de [bucket] está abierto: esta request no llegó a tocar la red.
 *
 * Se trata como un fallo transitorio del servidor -- misma UI que un 503, nunca como un
 * problema de conexión del propio dispositivo -- por eso cada repositorio la atrapa aparte
 * de un `IOException` genérico, antes de llegar a ese catch.
 */
class CircuitBreakerOpenException(bucket: String) :
    IOException("Circuito abierto para '$bucket': la request no se intentó contra la red.")

/**
 * Abre el circuito por servicio upstream -- según el primer segmento de ruta después de
 * `/api/v1/` (`auth`, `vehicles`, `alerts`, `notifications`) -- cuando ese servicio encadena
 * demasiadas fallas, y corta en seco las siguientes requests de ese mismo bucket sin tocar
 * la red. Va *antes* del interceptor de attestation en `NetworkModule`: con el circuito
 * abierto, tampoco se paga el costo de Play Integrity.
 *
 * Singleton en memoria, sin persistencia -- reinicia en cada arranque de proceso. Un estado
 * por bucket, no uno global: un upstream caído no debería abrir el circuito de los demás.
 * El mismo singleton se inyecta en los dos `OkHttpClient` de `NetworkModule`, así que el
 * estado de cada bucket es efectivamente compartido entre ambos clientes.
 *
 * Cuentan como falla un `IOException` (timeout, conexión rechazada) y cualquier `5xx`. Un
 * `4xx` del upstream se registra como éxito del circuito -- es evidencia de un problema con
 * *esta* request, no de que el servicio esté caído. Un fallo de attestation tampoco cuenta:
 * la request nunca salió del dispositivo, así que no es evidencia sobre el estado del
 * upstream.
 */
@Singleton
class CircuitBreakerInterceptor @Inject constructor(
    private val config: CircuitBreakerConfig,
) : Interceptor {

    private val states = ConcurrentHashMap<String, CircuitState>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val bucket = request.url.upstreamBucket()
        val state = states.getOrPut(bucket) { CircuitState() }
        val now = System.currentTimeMillis()

        if (!state.tryAcquire(now, config.openMs)) {
            throw CircuitBreakerOpenException(bucket)
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

    /** `/api/v1/vehicles/check` -> ["api", "v1", "vehicles", "check"][2] == "vehicles". */
    private fun okhttp3.HttpUrl.upstreamBucket(): String = pathSegments.getOrElse(2) { "desconocido" }

    private companion object {
        val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}

/**
 * Estado de un bucket. Closed (sin fallas recientes) -> Open (tras [CircuitBreakerConfig.
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
