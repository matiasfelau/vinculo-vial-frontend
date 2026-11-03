package ar.felaufernandezbottaro.vinculovial.data.account

import ar.felaufernandezbottaro.vinculovial.data.push.PendingFcmTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.FakeRefreshApi
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSessionKeyProvisioner
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionAead
import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.push.FakeFcmTokenProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * La limpieza local de la baja de cuenta (US-11) con las piezas reales -- `SecureTokenStore`,
 * `SessionAead`, `SessionManager`, `PendingFcmTokenStore` -- y sólo el Keystore y Firebase
 * sustituidos por fakes. Cada test afirma una de las cosas que el contrato manda borrar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalAccountDataEraserImplTest {

    private lateinit var provisioner: FakeSessionKeyProvisioner
    private lateinit var sessionPrefs: FakeSharedPreferences
    private lateinit var sessionAead: SessionAead
    private lateinit var tokenStore: SecureTokenStore
    private lateinit var fcmTokenProvider: FakeFcmTokenProvider
    private lateinit var pendingStore: PendingFcmTokenStore

    private val tokens = SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz")

    @Before
    fun setUp() {
        provisioner = FakeSessionKeyProvisioner()
        sessionPrefs = FakeSharedPreferences()
        sessionAead = SessionAead(provisioner)
        tokenStore = SecureTokenStore(sessionPrefs, sessionAead)
        tokenStore.save(tokens)
        fcmTokenProvider = FakeFcmTokenProvider()
        pendingStore = PendingFcmTokenStore(FakeSharedPreferences())
    }

    private fun eraser() = LocalAccountDataEraserImpl(
        sessionManager = SessionManager(FakeRefreshApi(), tokenStore, UnconfinedTestDispatcher()),
        sessionAead = sessionAead,
        fcmTokenProvider = fcmTokenProvider,
        pendingFcmTokenStore = pendingStore,
    )

    @Test
    fun `borra los tokens de sesion`() = runTest {
        eraser().eraseAll()

        assertNull(tokenStore.read())
        assertFalse(tokenStore.sessionState.value)
    }

    @Test
    fun `destruye las claves de Keystore de la sesion`() = runTest {
        eraser().eraseAll()

        assertEquals(1, provisioner.destroyCount)
        assertFalse(provisioner.hasPersistedKey)
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

    /** La regresión que motivó `SessionAead`: el login siguiente no puede quedar ilegible tras un reinicio. */
    @Test
    fun `un login posterior en el mismo proceso sobrevive a un reinicio de la app`() = runTest {
        eraser().eraseAll()

        val nextAccount = SessionTokens(accessToken = "access-otra-cuenta", refreshToken = "refresh-otra-cuenta")
        tokenStore.save(nextAccount)

        val afterRestart = SecureTokenStore(sessionPrefs, SessionAead(provisioner))
        assertEquals(nextAccount, afterRestart.read())
    }

    @Test
    fun `si destruir las claves falla, los tokens ya estan borrados y el resto de la limpieza corre igual`() = runTest {
        provisioner.destroyFailure = IllegalStateException("Keystore roto")
        pendingStore.savePendingRegistration("fcm-por-registrar")

        eraser().eraseAll()

        assertNull(tokenStore.read())
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

        assertEquals(1, provisioner.destroyCount)
        assertEquals(1, fcmTokenProvider.deleteCallCount)
    }
}
