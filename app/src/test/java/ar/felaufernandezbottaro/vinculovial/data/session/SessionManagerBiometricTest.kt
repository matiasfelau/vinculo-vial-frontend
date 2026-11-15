package ar.felaufernandezbottaro.vinculovial.data.session

import ar.felaufernandezbottaro.vinculovial.data.remote.auth.RefreshApi
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshResponseDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshTokenRequestDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import javax.crypto.Cipher

/**
 * Bloqueo, desbloqueo y activación de la biometría (US-12) sobre `SessionManager`, con la clave
 * biométrica reemplazada por [FakeSessionKeyVault].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerBiometricTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var vault: FakeSessionKeyVault
    private lateinit var tokenStore: SecureTokenStore
    private lateinit var refreshApi: FakeRefreshApi

    private val tokens = SessionTokens(accessToken = "access-viejo", refreshToken = "refresh-viejo")
    private val renewed = SessionTokens(accessToken = "access-nuevo", refreshToken = "refresh-nuevo")

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        vault = FakeSessionKeyVault()
        tokenStore = SecureTokenStore(prefs, vault)
        refreshApi = FakeRefreshApi()
    }

    private fun manager(api: RefreshApi = refreshApi) =
        SessionManager(api, tokenStore, UnconfinedTestDispatcher())

    private fun errorResponse(code: Int) =
        Response.error<RefreshResponseDto>(code, "".toResponseBody("application/json".toMediaType()))

    private fun unlockedWithBiometric() {
        tokenStore.startSession(tokens)
        assertTrue(tokenStore.enableBiometric())
    }

    /** Lo que queda tras bloquear: sólo el blob, igual que tras matar el proceso. */
    private fun lockedWithBiometric() {
        unlockedWithBiometric()
        tokenStore.lock()
    }

    private fun authenticatedCipher(): Cipher = vault.createUnlockCipher()

    private fun openBlob(): String =
        String(vault.createUnlockCipher().doFinal(requireNotNull(tokenStore.readWrappedRefresh())))

    // --- Bloqueo ---

    @Test
    fun `bloquear con biometria deja LOCKED sin llamar a la red y conserva el blob`() = runTest {
        unlockedWithBiometric()

        manager().lock()

        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
        assertNull(tokenStore.read())
        assertEquals(0, refreshApi.logoutCallCount)
        assertEquals(tokens.refreshToken, openBlob())
    }

    @Test
    fun `bloquear sin biometria cierra la sesion en el servidor y deja NONE`() = runTest {
        tokenStore.startSession(tokens)

        manager().lock()

        assertEquals(listOf(tokens.refreshToken), refreshApi.receivedLogoutTokens)
        assertEquals(SessionState.NONE, tokenStore.sessionState.value)
        assertNull(tokenStore.read())
    }

    @Test
    fun `bloquear sin biometria deja NONE aunque el logout falle`() = runTest {
        tokenStore.startSession(tokens)
        refreshApi.logoutExceptionToThrow = IOException("sin conexion")

        manager().lock()

        assertEquals(SessionState.NONE, tokenStore.sessionState.value)
    }

    @Test
    fun `bloquear una sesion que no esta desbloqueada no hace nada`() = runTest {
        lockedWithBiometric()

        manager().lock()

        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
        assertEquals(0, refreshApi.logoutCallCount)
        assertNotNull(tokenStore.readWrappedRefresh())
    }

    @Test
    fun `bloquear sin ninguna sesion no llama a la red`() = runTest {
        manager().lock()

        assertEquals(SessionState.NONE, tokenStore.sessionState.value)
        assertEquals(0, refreshApi.logoutCallCount)
    }

    /** Rotar y re-envolver van dentro del mismo lock: un bloqueo no puede colarse en el medio. */
    @Test
    fun `el bloqueo espera a un refresh en vuelo y el blob queda con el refresh nuevo`() = runTest {
        unlockedWithBiometric()
        val gate = CompletableDeferred<Unit>()
        val gatedApi = object : RefreshApi {
            override suspend fun refresh(body: RefreshTokenRequestDto): Response<RefreshResponseDto> {
                gate.await()
                return Response.success(RefreshResponseDto(renewed.accessToken, renewed.refreshToken))
            }

            override suspend fun logout(body: RefreshTokenRequestDto): Response<Unit> = Response.success(Unit)
        }
        val manager = manager(gatedApi)

        val refresh = async(start = CoroutineStart.UNDISPATCHED) { manager.refreshSession(tokens.accessToken) }
        val lock = async(start = CoroutineStart.UNDISPATCHED) { manager.lock() }

        assertFalse("el bloqueo tiene que esperar a la rotación", lock.isCompleted)

        gate.complete(Unit)
        refresh.await()
        lock.await()

        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
        assertEquals(renewed.refreshToken, openBlob())
    }

    // --- Preparar el desbloqueo ---

    @Test
    fun `prepareUnlock entrega el Cipher para el prompt cuando la sesion esta LOCKED`() = runTest {
        lockedWithBiometric()

        val preparation = manager().prepareUnlock()

        assertTrue(preparation is UnlockPreparation.Ready)
        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
    }

    @Test
    fun `si la clave quedo invalidada se borra todo y la sesion queda NONE`() = runTest {
        lockedWithBiometric()
        vault.invalidated = true

        val preparation = manager().prepareUnlock()

        assertEquals(UnlockPreparation.KeyInvalidated, preparation)
        assertEquals(SessionState.NONE, tokenStore.sessionState.value)
        assertNull(tokenStore.readWrappedRefresh())
        assertFalse(vault.hasKey)
    }

    @Test
    fun `prepareUnlock fuera de LOCKED no entrega Cipher`() = runTest {
        tokenStore.startSession(tokens)

        assertEquals(UnlockPreparation.Unavailable, manager().prepareUnlock())
    }

    // --- Desbloqueo ---

    @Test
    fun `desbloquear abre el blob, refresca y vuelve a envolver el refresh nuevo`() = runTest {
        lockedWithBiometric()

        val outcome = manager().unlock(authenticatedCipher())

        assertEquals(UnlockOutcome.Unlocked, outcome)
        assertEquals(listOf(tokens.refreshToken), refreshApi.receivedRefreshTokens)
        assertEquals(SessionState.UNLOCKED, tokenStore.sessionState.value)
        assertEquals(renewed, tokenStore.read())
        assertEquals(renewed.refreshToken, openBlob())
    }

    @Test
    fun `un 401 en el desbloqueo borra blob y clave y deja NONE`() = runTest {
        lockedWithBiometric()
        refreshApi.response = errorResponse(401)

        val outcome = manager().unlock(authenticatedCipher())

        assertEquals(UnlockOutcome.Expired, outcome)
        assertEquals(SessionState.NONE, tokenStore.sessionState.value)
        assertNull(tokenStore.readWrappedRefresh())
        assertFalse(vault.hasKey)
    }

    @Test
    fun `sin red el desbloqueo sigue LOCKED y no borra el blob`() = runTest {
        lockedWithBiometric()
        refreshApi.exceptionToThrow = IOException("sin conexion")

        val outcome = manager().unlock(authenticatedCipher())

        assertEquals(UnlockOutcome.Unavailable, outcome)
        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
        assertEquals(tokens.refreshToken, openBlob())
        assertTrue(vault.hasKey)
    }

    @Test
    fun `un 5xx en el desbloqueo sigue LOCKED y no borra el blob`() = runTest {
        lockedWithBiometric()
        refreshApi.response = errorResponse(503)

        val outcome = manager().unlock(authenticatedCipher())

        assertEquals(UnlockOutcome.Unavailable, outcome)
        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
        assertNotNull(tokenStore.readWrappedRefresh())
    }

    @Test
    fun `un fallo de runtime como el breaker abierto sigue LOCKED y no borra el blob`() = runTest {
        lockedWithBiometric()
        refreshApi.exceptionToThrow = IllegalStateException("breaker abierto")

        val outcome = manager().unlock(authenticatedCipher())

        assertEquals(UnlockOutcome.Unavailable, outcome)
        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
        assertNotNull(tokenStore.readWrappedRefresh())
    }

    /** El contrato del desbloqueo sólo da por vencida la sesión con un 401. */
    @Test
    fun `un 403 en el desbloqueo no borra el blob`() = runTest {
        lockedWithBiometric()
        refreshApi.response = errorResponse(403)

        val outcome = manager().unlock(authenticatedCipher())

        assertEquals(UnlockOutcome.Unavailable, outcome)
        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
        assertNotNull(tokenStore.readWrappedRefresh())
    }

    @Test
    fun `reintentar tras una falla de red entra sin pedir la contrasena`() = runTest {
        lockedWithBiometric()
        refreshApi.exceptionToThrow = IOException("modo avion")
        val manager = manager()
        assertEquals(UnlockOutcome.Unavailable, manager.unlock(authenticatedCipher()))

        refreshApi.exceptionToThrow = null
        val retry = manager.unlock(authenticatedCipher())

        assertEquals(UnlockOutcome.Unlocked, retry)
        assertEquals(renewed, tokenStore.read())
    }

    @Test
    fun `un blob que no se puede abrir se trata como sesion vencida`() = runTest {
        lockedWithBiometric()
        prefs.edit().putString("session_refresh_wrapped", "AAAA").apply()

        val outcome = manager().unlock(authenticatedCipher())

        assertEquals(UnlockOutcome.Expired, outcome)
        assertEquals(SessionState.NONE, tokenStore.sessionState.value)
        assertEquals(0, refreshApi.callCount)
    }

    @Test
    fun `desbloquear una sesion ya desbloqueada no vuelve a llamar a refresh`() = runTest {
        unlockedWithBiometric()

        val outcome = manager().unlock(authenticatedCipher())

        assertEquals(UnlockOutcome.Unlocked, outcome)
        assertEquals(0, refreshApi.callCount)
    }

    @Test
    fun `desbloquear sin sesion no llama a la red y la da por vencida`() = runTest {
        val outcome = manager().unlock(SessionWrapCipher.newCipher())

        assertEquals(UnlockOutcome.Expired, outcome)
        assertEquals(0, refreshApi.callCount)
    }

    // --- Activar y desactivar ---

    @Test
    fun `enableBiometric activa el desbloqueo con la sesion actual`() = runTest {
        tokenStore.startSession(tokens)

        assertTrue(manager().enableBiometric())

        assertTrue(tokenStore.biometricEnabled.value)
        assertEquals(tokens.refreshToken, openBlob())
    }

    @Test
    fun `disableBiometric borra blob y clave y la sesion sigue desbloqueada`() = runTest {
        unlockedWithBiometric()

        manager().disableBiometric()

        assertFalse(tokenStore.biometricEnabled.value)
        assertNull(tokenStore.readWrappedRefresh())
        assertFalse(vault.hasKey)
        assertEquals(SessionState.UNLOCKED, tokenStore.sessionState.value)
    }
}
