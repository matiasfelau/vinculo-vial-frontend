package ar.felaufernandezbottaro.vinculovial.data.remote

import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agrega el veredicto de integridad a cada request. El token se pide por request y se
 * liga al cuerpo, así nunca viaja uno vencido ni reutilizable en otra llamada.
 *
 * En [BEST_EFFORT_PATHS] es best-effort: si no se puede generar, la request sigue sin el
 * header en vez de abortar. En cualquier otro endpoint, ese mismo fallo sigue siendo
 * bloqueante.
 * - `/logout` (contrato US-03): el usuario tiene que poder cerrar sesión aunque la
 *   attestation esté rota.
 * - `/vehicles` (contrato US-04): protegido por JWT, no por App Attestation -- Kong nunca
 *   valida este header acá, así que exigirlo sería un punto de falla local sin ninguna
 *   contrapartida de seguridad del lado del servidor.
 * - `/alerts/attempts` y `/alerts` (contratos US-05 y US-08): misma decisión de velocidad --
 *   se compensan con Rate Limiting estricto en Kong por `X-User-Id`, no con attestation.
 */
@Singleton
class AttestationInterceptor @Inject constructor(
    private val attestationProvider: AttestationProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Los interceptores de OkHttp son bloqueantes y ya corren fuera del hilo
        // principal, así que acá `runBlocking` es la forma habitual de consumir un
        // proveedor suspendido (Play Integrity expone su API como Task asíncrona).
        val token = try {
            runBlocking { attestationProvider.tokenFor(request.bodyBytes()) }
        } catch (e: AttestationUnavailableException) {
            if (request.url.encodedPath in BEST_EFFORT_PATHS) return chain.proceed(request)
            throw e
        }

        return chain.proceed(request.newBuilder().header(HEADER, token).build())
    }

    /** Cuerpo ya serializado; es lo que se usa como `requestHash` del token. */
    private fun okhttp3.Request.bodyBytes(): ByteArray {
        val body = body ?: return ByteArray(0)
        return Buffer().use { buffer ->
            body.writeTo(buffer)
            buffer.readByteArray()
        }
    }

    companion object {
        const val HEADER = "X-App-Attestation"
        private val BEST_EFFORT_PATHS = setOf(
            "/api/v1/auth/logout",
            "/api/v1/vehicles",
            "/api/v1/alerts/attempts",
            "/api/v1/alerts",
        )
    }
}
