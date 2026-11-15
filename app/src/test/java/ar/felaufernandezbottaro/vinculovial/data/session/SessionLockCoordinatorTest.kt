package ar.felaufernandezbottaro.vinculovial.data.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Los disparadores de bloqueo que no son la muerte del proceso (US-12): la Activity que termina y
 * volver a primer plano tras `SESSION_LOCK_AFTER_BACKGROUND_SECONDS` o más en segundo plano.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionLockCoordinatorTest {

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var refreshApi: FakeRefreshApi
    private var nowMillis = 0L

    private val tokens = SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz")

    @Before
    fun setUp() {
        tokenStore = SecureTokenStore(FakeSharedPreferences(), FakeSessionKeyVault())
        refreshApi = FakeRefreshApi()
        nowMillis = 0L
    }

    private fun coordinator(lockAfterSeconds: Long = LOCK_AFTER_SECONDS): SessionLockCoordinator {
        val dispatcher = UnconfinedTestDispatcher()
        return SessionLockCoordinator(
            sessionManager = SessionManager(refreshApi, tokenStore, dispatcher),
            clock = MonotonicClock { nowMillis },
            lockAfterBackgroundSeconds = lockAfterSeconds,
            scope = CoroutineScope(dispatcher),
        )
    }

    private fun unlockedWithBiometric() {
        tokenStore.startSession(tokens)
        tokenStore.enableBiometric()
    }

    @Test
    fun `volver antes del umbral no bloquea`() {
        unlockedWithBiometric()
        val coordinator = coordinator()

        coordinator.onAppBackgrounded()
        nowMillis += (LOCK_AFTER_SECONDS * MILLIS_PER_SECOND) - 1
        coordinator.onAppForegrounded()

        assertEquals(SessionState.UNLOCKED, tokenStore.sessionState.value)
    }

    @Test
    fun `volver tras el umbral exacto con biometria deja LOCKED`() {
        unlockedWithBiometric()
        val coordinator = coordinator()

        coordinator.onAppBackgrounded()
        nowMillis += LOCK_AFTER_SECONDS * MILLIS_PER_SECOND
        coordinator.onAppForegrounded()

        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `volver tras el umbral sin biometria cierra la sesion en el servidor y deja NONE`() {
        tokenStore.startSession(tokens)
        val coordinator = coordinator()

        coordinator.onAppBackgrounded()
        nowMillis += LOCK_AFTER_SECONDS * MILLIS_PER_SECOND * 2
        coordinator.onAppForegrounded()

        assertEquals(SessionState.NONE, tokenStore.sessionState.value)
        assertEquals(listOf(tokens.refreshToken), refreshApi.receivedLogoutTokens)
    }

    @Test
    fun `el umbral sale del parametro, no de un valor fijo`() {
        unlockedWithBiometric()
        val coordinator = coordinator(lockAfterSeconds = 1)

        coordinator.onAppBackgrounded()
        nowMillis += MILLIS_PER_SECOND
        coordinator.onAppForegrounded()

        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
    }

    @Test
    fun `volver a primer plano sin haber pasado por segundo plano no bloquea`() {
        unlockedWithBiometric()
        val coordinator = coordinator()

        nowMillis += LOCK_AFTER_SECONDS * MILLIS_PER_SECOND * 10
        coordinator.onAppForegrounded()

        assertEquals(SessionState.UNLOCKED, tokenStore.sessionState.value)
    }

    @Test
    fun `cada ida a segundo plano mide desde cero`() {
        unlockedWithBiometric()
        val coordinator = coordinator()

        coordinator.onAppBackgrounded()
        nowMillis += LOCK_AFTER_SECONDS * MILLIS_PER_SECOND - 1
        coordinator.onAppForegrounded()
        nowMillis += LOCK_AFTER_SECONDS * MILLIS_PER_SECOND * 10
        coordinator.onAppBackgrounded()
        nowMillis += 1
        coordinator.onAppForegrounded()

        assertEquals(SessionState.UNLOCKED, tokenStore.sessionState.value)
    }

    @Test
    fun `la Activity que termina bloquea con biometria`() {
        unlockedWithBiometric()

        coordinator().onActivityFinished()

        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
    }

    @Test
    fun `la Activity que termina sin biometria cierra la sesion`() {
        tokenStore.startSession(tokens)

        coordinator().onActivityFinished()

        assertEquals(SessionState.NONE, tokenStore.sessionState.value)
        assertEquals(1, refreshApi.logoutCallCount)
    }

    @Test
    fun `bloquear dos veces seguidas no repite el logout ni toca el blob`() {
        unlockedWithBiometric()
        val coordinator = coordinator()

        coordinator.onActivityFinished()
        coordinator.onActivityFinished()

        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
        assertEquals(0, refreshApi.logoutCallCount)
    }

    private companion object {
        const val LOCK_AFTER_SECONDS = 300L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
