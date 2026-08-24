package ar.felaufernandezbottaro.vinculovial.data.remote.auth

import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.data.session.SessionRefreshOutcome
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Atrapa el `401` de cualquier endpoint autenticado y, antes de darlo por perdido, intenta
 * un refresh silencioso. Todo el Double-Checked Locking vive en [SessionManager]; acá sólo
 * se decide si corresponde reintentar la request original.
 *
 * `/refresh` y `/logout` no pasan por acá en absoluto: viven en un `OkHttpClient` separado,
 * sin este `Authenticator` instalado (ver `NetworkModule`) -- así se corta la dependencia
 * circular (este `Authenticator` depende de [SessionManager], que depende de `RefreshApi`,
 * que necesitaría este mismo cliente) y de paso queda garantizado en el tipo, no sólo por
 * comparar paths, que un 401 de esos dos endpoints nunca dispara otro refresh.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionManager: SessionManager,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Ya se reintentó una vez para esta request: si el reintento también volvió 401, no
        // hay que insistir -- evita loops si el token "nuevo" sigue siendo rechazado.
        if (response.priorResponse != null) return null

        val failedAccessToken = response.request.bearerToken() ?: return null

        // Los interceptores de OkHttp/Authenticator son bloqueantes y ya corren fuera del
        // hilo principal, así que `runBlocking` es la forma habitual de consumir acá una
        // función suspendida (mismo patrón que `AttestationInterceptor`).
        return when (val outcome = runBlocking { sessionManager.refreshSession(failedAccessToken) }) {
            is SessionRefreshOutcome.Renewed ->
                response.request.newBuilder()
                    .header(AUTHORIZATION_HEADER, BEARER_PREFIX + outcome.tokens.accessToken)
                    .build()

            SessionRefreshOutcome.LoggedOut,
            SessionRefreshOutcome.Unavailable,
            -> null
        }
    }

    private fun Request.bearerToken(): String? =
        header(AUTHORIZATION_HEADER)?.removePrefix(BEARER_PREFIX)?.takeIf { it.isNotBlank() }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
