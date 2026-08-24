package ar.felaufernandezbottaro.vinculovial.data.session

import ar.felaufernandezbottaro.vinculovial.data.remote.auth.RefreshApi
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshTokenRequestDto
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que pasó al intentar refrescar, visto por quien pidió el refresh (Authenticator, Logout). */
sealed interface SessionRefreshOutcome {

    /** El par vigente tras el intento: el que rotó esta llamada, o el que ya había rotado otra. */
    data class Renewed(val tokens: SessionTokens) : SessionRefreshOutcome

    /** No hay sesión vigente tras el intento -- ni había, ni el backend la convalidó (401/403). */
    data object LoggedOut : SessionRefreshOutcome

    /** Fallo transitorio (red, attestation del dispositivo, 5xx): la sesión local sigue intacta. */
    data object Unavailable : SessionRefreshOutcome
}

/**
 * Única puerta de entrada para refrescar la sesión y para borrarla. Reúne en un solo lugar
 * el Double-Checked Locking que exige RTR estricto: varias peticiones en vuelo pueden recibir
 * `401` a la vez, y sin coordinación cada una dispararía su propio `/refresh` -- la segunda
 * llegaría con un token ya rotado por la primera y el backend la trataría como reúso,
 * revocando todas las sesiones del usuario (Defensa Activa).
 *
 * 1. Todo el refresh vive dentro de un único [Mutex]: la primera corrutina que entra ejecuta
 *    el refresh, las demás esperan.
 * 2. Al adquirir el lock, cada corrutina relee el token del [SecureTokenStore] y lo compara
 *    con el que produjo su 401. Si ya cambió, otra corrutina rotó por ella: sale sin llamar a
 *    `/refresh` y devuelve el par nuevo tal cual.
 *
 * Esa relectura no es defensiva ni opcional: sin ella, la corrutina encolada quemaría el
 * token recién rotado como si fuera el suyo.
 */
@Singleton
class SessionManager @Inject constructor(
    private val refreshApi: RefreshApi,
    private val tokenStore: SecureTokenStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private val mutex = Mutex()

    /**
     * @param failedAccessToken el access token que produjo el 401 que disparó este refresh.
     * Se usa sólo para el double-checked locking, nunca se reenvía: lo que viaja a
     * `/refresh` es siempre el refresh token leído recién adquirido el lock.
     */
    suspend fun refreshSession(failedAccessToken: String): SessionRefreshOutcome = mutex.withLock {
        withContext(dispatcher) {
            val current = tokenStore.read() ?: return@withContext SessionRefreshOutcome.LoggedOut

            if (current.accessToken != failedAccessToken) {
                return@withContext SessionRefreshOutcome.Renewed(current)
            }

            try {
                val response = refreshApi.refresh(RefreshTokenRequestDto(current.refreshToken))
                val body = response.body()
                when {
                    response.isSuccessful && body != null -> {
                        val renewed = SessionTokens(accessToken = body.accessToken, refreshToken = body.refreshToken)
                        tokenStore.save(renewed)
                        SessionRefreshOutcome.Renewed(renewed)
                    }

                    response.code() == HTTP_UNAUTHORIZED || response.code() == HTTP_FORBIDDEN -> {
                        clearSession()
                        SessionRefreshOutcome.LoggedOut
                    }

                    // 2xx sin cuerpo (contrato roto) o cualquier otro código: no hay
                    // evidencia de que la sesión sea inválida, no se la borra por las dudas.
                    else -> SessionRefreshOutcome.Unavailable
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Sin conexión, DNS, timeout, o attestation del dispositivo no disponible.
                SessionRefreshOutcome.Unavailable
            } catch (e: RuntimeException) {
                SessionRefreshOutcome.Unavailable
            }
        }
    }

    /** Limpieza centralizada: la llaman el refresh fallido, el logout y el logout forzado. */
    fun clearSession() {
        tokenStore.clear()
    }

    /**
     * Llama a `/logout` sin exigir attestation (best-effort, ver `AttestationInterceptor`) y,
     * sea cual sea el resultado -- 200, sin sesión que cerrar, error de red -- limpia la
     * sesión local igual. El usuario nunca queda atrapado en una sesión que quiso cerrar.
     *
     * Comparte el [Mutex] con [refreshSession]: si hay un refresh en vuelo, el logout espera a
     * que termine y limpia sobre el resultado final, en vez de arriesgarse a que un refresh
     * que gana la carrera después del clear resucite la sesión.
     */
    suspend fun logout() = mutex.withLock {
        withContext(dispatcher) {
            tokenStore.read()?.let { current ->
                try {
                    refreshApi.logout(RefreshTokenRequestDto(current.refreshToken))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    // Sin conexión: no importa, se limpia igual.
                } catch (e: RuntimeException) {
                    // Cualquier otro fallo tampoco debe trabar el logout.
                }
            }
            clearSession()
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
