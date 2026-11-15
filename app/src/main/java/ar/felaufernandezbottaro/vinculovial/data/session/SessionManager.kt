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
import java.security.GeneralSecurityException
import javax.crypto.Cipher
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

/** Resultado de desbloquear la sesión con la biometría. */
sealed interface UnlockOutcome {

    /** Refresh exitoso: la sesión está desbloqueada (o ya lo estaba). */
    data object Unlocked : UnlockOutcome

    /** El servidor rechazó el refresh (401) o el blob no se pudo abrir: se borró todo, estado NONE. */
    data object Expired : UnlockOutcome

    /** Red, 5xx o breaker abierto: sigue LOCKED y el blob queda intacto, para reintentar. */
    data object Unavailable : UnlockOutcome
}

/** Resultado de preparar el `Cipher` que usa el prompt biométrico. */
sealed interface UnlockPreparation {

    data class Ready(val cipher: Cipher) : UnlockPreparation

    /** La clave quedó invalidada (cambiaron las huellas): ya se borró todo, estado NONE. */
    data object KeyInvalidated : UnlockPreparation

    /** No se pudo preparar el descifrado, pero la clave no está invalidada: se puede reintentar. */
    data object Unavailable : UnlockPreparation
}

/**
 * Única puerta de entrada para refrescar, bloquear, desbloquear y borrar la sesión. Reúne en un
 * solo lugar el Double-Checked Locking que exige RTR estricto: varias peticiones en vuelo pueden
 * recibir `401` a la vez, y sin coordinación cada una dispararía su propio `/refresh` -- la
 * segunda llegaría con un token ya rotado por la primera y el backend la trataría como reúso,
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
 *
 * Con la biometría activa, volver a envolver el refresh recién rotado ocurre dentro de ese mismo
 * lock ([SecureTokenStore.rotate]): ni un bloqueo, ni un logout ni una baja pueden colarse entre
 * la rotación y el blob.
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
                        tokenStore.rotate(renewed)
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
     * sesión local igual, incluido el blob y la clave biométrica. El usuario nunca queda
     * atrapado en una sesión que quiso cerrar.
     *
     * Comparte el [Mutex] con [refreshSession]: si hay un refresh en vuelo, el logout espera a
     * que termine y limpia sobre el resultado final, en vez de arriesgarse a que un refresh
     * que gana la carrera después del clear resucite la sesión.
     */
    suspend fun logout() = mutex.withLock {
        withContext(dispatcher) {
            logoutRemotely()
            clearSession()
        }
    }

    /**
     * Borrado de la sesión por baja de cuenta (US-11). A diferencia de [clearSession] suelto,
     * toma el [Mutex]: un refresh en vuelo termina antes de borrar -- si no, podría guardar un
     * par recién rotado *después* del borrado y resucitar una sesión de una cuenta que ya no
     * existe -- y uno posterior ya no encuentra sesión que refrescar. Borra también el blob y la
     * clave biométrica.
     */
    suspend fun clearSessionForAccountDeletion() = mutex.withLock {
        withContext(dispatcher) { clearSession() }
    }

    /**
     * Bloqueo (US-12): borra access y refresh de la memoria. Con biometría activada la sesión
     * queda LOCKED. Sin ella, se cierra la sesión en el servidor (best-effort) y queda NONE.
     * Si no hay sesión desbloqueada, no hace nada.
     */
    suspend fun lock() = mutex.withLock {
        withContext(dispatcher) {
            if (!tokenStore.hasSession()) return@withContext
            if (tokenStore.biometricEnabled.value) {
                tokenStore.lock()
            } else {
                logoutRemotely()
                clearSession()
            }
        }
    }

    /**
     * El `Cipher` para el prompt biométrico. Si la clave quedó invalidada (cambiaron las
     * huellas), borra blob y clave y deja la sesión en NONE: el usuario entra con la contraseña.
     */
    suspend fun prepareUnlock(): UnlockPreparation = mutex.withLock {
        withContext(dispatcher) {
            if (tokenStore.sessionState.value != SessionState.LOCKED) return@withContext UnlockPreparation.Unavailable
            try {
                UnlockPreparation.Ready(tokenStore.createUnlockCipher())
            } catch (e: SessionKeyInvalidatedException) {
                clearSession()
                UnlockPreparation.KeyInvalidated
            } catch (e: RuntimeException) {
                UnlockPreparation.Unavailable
            }
        }
    }

    /**
     * Desbloqueo (US-12): abre el blob con el [cipher] ya autenticado por la biometría, pide un
     * refresh y vuelve a envolver el refresh nuevo, todo bajo el mismo lock.
     *
     * - 401: se borran blob y clave, estado NONE ([UnlockOutcome.Expired]).
     * - Red, 5xx, breaker abierto o cualquier otro código: sigue LOCKED y **nunca** borra el blob
     *   ([UnlockOutcome.Unavailable]). El 403 también cae acá: a diferencia de [refreshSession],
     *   el contrato del desbloqueo sólo da por vencida la sesión con un 401.
     */
    suspend fun unlock(cipher: Cipher): UnlockOutcome = mutex.withLock {
        withContext(dispatcher) {
            when (tokenStore.sessionState.value) {
                SessionState.UNLOCKED -> return@withContext UnlockOutcome.Unlocked
                SessionState.NONE -> return@withContext UnlockOutcome.Expired
                SessionState.LOCKED -> Unit
            }

            val refreshToken = openWrappedRefresh(cipher)
                ?: run {
                    clearSession()
                    return@withContext UnlockOutcome.Expired
                }

            try {
                val response = refreshApi.refresh(RefreshTokenRequestDto(refreshToken))
                val body = response.body()
                when {
                    response.isSuccessful && body != null -> {
                        tokenStore.unlockWith(SessionTokens(body.accessToken, body.refreshToken))
                        UnlockOutcome.Unlocked
                    }

                    response.code() == HTTP_UNAUTHORIZED -> {
                        clearSession()
                        UnlockOutcome.Expired
                    }

                    else -> UnlockOutcome.Unavailable
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                UnlockOutcome.Unavailable
            } catch (e: RuntimeException) {
                UnlockOutcome.Unavailable
            }
        }
    }

    /** `null` si no hay blob o el criptograma no se pudo abrir: en ambos casos no hay sesión que recuperar. */
    private fun openWrappedRefresh(cipher: Cipher): String? {
        val wrapped = tokenStore.readWrappedRefresh() ?: return null
        val plaintext = try {
            cipher.doFinal(wrapped)
        } catch (e: GeneralSecurityException) {
            return null
        }
        return try {
            String(plaintext, Charsets.UTF_8)
        } finally {
            plaintext.fill(0)
        }
    }

    /** `/logout` best-effort con el refresh en memoria, si lo hay. Un fallo nunca traba nada. */
    private suspend fun logoutRemotely() {
        val current = tokenStore.read() ?: return
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

    /** Activar la biometría (Settings u ofrecimiento tras el login). Bajo el lock: no compite con una rotación. */
    suspend fun enableBiometric(): Boolean = mutex.withLock {
        withContext(dispatcher) { tokenStore.enableBiometric() }
    }

    /** Desactivar la biometría desde Settings: borra blob y clave. */
    suspend fun disableBiometric() = mutex.withLock {
        withContext(dispatcher) { tokenStore.disableBiometric() }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
