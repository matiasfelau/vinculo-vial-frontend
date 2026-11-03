package ar.felaufernandezbottaro.vinculovial.data.remote.auth

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.ApiErrorDto
import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.data.session.SessionRefreshOutcome
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
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
    moshi: Moshi,
) : Authenticator {

    private val errorAdapter = moshi.adapter(ApiErrorDto::class.java)

    override fun authenticate(route: Route?, response: Response): Request? {
        // Ya se reintentó una vez para esta request: si el reintento también volvió 401, no
        // hay que insistir -- evita loops si el token "nuevo" sigue siendo rechazado.
        if (response.priorResponse != null) return null

        // Un 401 que trae el `code` de la propia aplicación no es una sesión vencida (eso lo
        // dice Kong, sin `code`): es la baja de cuenta (US-11) rechazando la contraseña. El
        // access token es válido; refrescar y reintentar sólo gastaría un segundo intento del
        // límite de la baja (3/min) y rotaría el refresh token sin ningún motivo.
        if (response.isInvalidPassword()) return null

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

    /**
     * Mira el cuerpo sin consumirlo (`peekBody`): el repositorio todavía tiene que leerlo. Un
     * cuerpo ausente, ilegible o que no es el JSON del contrato cuenta como "no", y el 401 sigue
     * el camino de siempre.
     */
    private fun Response.isInvalidPassword(): Boolean {
        if (body == null) return false
        val raw = try {
            peekBody(MAX_ERROR_BODY_BYTES).string()
        } catch (e: IOException) {
            return false
        }
        return try {
            errorAdapter.fromJson(raw)?.code == CODE_INVALID_PASSWORD
        } catch (e: IOException) {
            false
        } catch (e: JsonDataException) {
            false
        }
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val CODE_INVALID_PASSWORD = "INVALID_PASSWORD"
        const val MAX_ERROR_BODY_BYTES = 4096L
    }
}
