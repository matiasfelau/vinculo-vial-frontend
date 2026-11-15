package ar.felaufernandezbottaro.vinculovial.data.account

import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import ar.felaufernandezbottaro.vinculovial.data.push.PendingFcmTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.FakeRefreshApi
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSessionKeyVault
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionKeyVault
import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.data.session.SessionState
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.push.FakeFcmTokenProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La limpieza local de la baja de cuenta (US-11) con las piezas reales -- `SecureTokenStore`,
 * `SessionManager`, `PendingFcmTokenStore` -- y sólo el Keystore y Firebase sustituidos por
 * fakes. Cada test afirma una de las cosas que el contrato manda borrar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalAccountDataEraserImplTest {

    private lateinit var vault: FakeSessionKeyVault
    private lateinit var sessionPrefs: FakeSharedPreferences
    private lateinit var tokenStore: SecureTokenStore
    private lateinit var fcmTokenProvider: FakeFcmTokenProvider
    private lateinit var pendingStore: PendingFcmTokenStore

    private val tokens = SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz")

    @Before
    fun setUp() {
        vault = FakeSessionKeyVault()
        sessionPrefs = FakeSharedPreferences()
        tokenStore = SecureTokenStore(sessionPrefs, vault)
        tokenStore.startSession(tokens)
        fcmTokenProvider = FakeFcmTokenProvider()
        pendingStore = PendingFcmTokenStore(FakeSharedPreferences())
    }

    private fun eraser() = LocalAccountDataEraserImpl(
        sessionManager = SessionManager(FakeRefreshApi(), tokenStore, UnconfinedTestDispatcher()),
        fcmTokenProvider = fcmTokenProvider,
        pendingFcmTokenStore = pendingStore,
    )

    @Test
    fun `borra los tokens de sesion`() = runTest {
        eraser().eraseAll()

        assertNull(tokenStore.read())
        assertEquals(SessionState.NONE, tokenStore.sessionState.value)
    }

    @Test
    fun `con la biometria activa borra tambien el blob y la clave`() = runTest {
        assertTrue(tokenStore.enableBiometric())

        eraser().eraseAll()

        assertNull(tokenStore.readWrappedRefresh())
        assertFalse(tokenStore.biometricEnabled.value)
        assertFalse(vault.hasKey)
        assertTrue("no queda nada de la sesión en disco", sessionPrefs.getAll().isEmpty())
    }

    @Test
    fun `invalida el token FCM del dispositivo`() = runTest {
        eraser().eraseAll()

        assertEquals(1, fcmTokenProvider.deleteCallCount)
    }

    @Test
    fun `borra los tokens FCM pendientes de alta y de baja`() = runTest {
        pendingStore.savePendingRegistration("fcm-por-registrar")
        pendingStore.savePendingDeletion("fcm-por-borrar")

        eraser().eraseAll()

        assertNull(pendingStore.readPendingRegistration())
        assertNull(pendingStore.readPendingDeletion())
    }

    @Test
    fun `un login posterior en el mismo proceso arranca limpio, sin biometria heredada`() = runTest {
        assertTrue(tokenStore.enableBiometric())
        eraser().eraseAll()

        val nextAccount = SessionTokens(accessToken = "access-otra-cuenta", refreshToken = "refresh-otra-cuenta")
        tokenStore.startSession(nextAccount)

        assertEquals(nextAccount, tokenStore.read())
        assertFalse(tokenStore.biometricEnabled.value)
        assertNull(tokenStore.readWrappedRefresh())
    }

    @Test
    fun `si el Keystore falla al borrar la clave, la sesion ya esta borrada y el resto de la limpieza corre igual`() = runTest {
        assertTrue(tokenStore.enableBiometric())
        val failingVault = object : SessionKeyVault by vault {
            override fun deleteKey() = throw CryptoOperationException("Keystore roto")
        }
        val store = SecureTokenStore(sessionPrefs, failingVault).also { it.startSession(tokens) }
        pendingStore.savePendingRegistration("fcm-por-registrar")

        LocalAccountDataEraserImpl(
            sessionManager = SessionManager(FakeRefreshApi(), store, UnconfinedTestDispatcher()),
            fcmTokenProvider = fcmTokenProvider,
            pendingFcmTokenStore = pendingStore,
        ).eraseAll()

        assertNull(store.read())
        assertEquals(1, fcmTokenProvider.deleteCallCount)
        assertNull(pendingStore.readPendingRegistration())
    }

    @Test
    fun `si el SDK no puede invalidar el token FCM, igual se limpian los pendientes`() = runTest {
        fcmTokenProvider.deleteSucceeds = false
        pendingStore.savePendingDeletion("fcm-por-borrar")

        eraser().eraseAll()

        assertNull(pendingStore.readPendingDeletion())
        assertNull(tokenStore.read())
    }

    @Test
    fun `sin sesion ni nada pendiente tambien termina sin error`() = runTest {
        tokenStore.clear()

        eraser().eraseAll()

        assertEquals(1, fcmTokenProvider.deleteCallCount)
    }
}
