package ar.felaufernandezbottaro.vinculovial.domain.account

import ar.felaufernandezbottaro.vinculovial.data.account.LocalAccountDataEraserImpl
import ar.felaufernandezbottaro.vinculovial.data.push.PendingFcmTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.FakeRefreshApi
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSessionKeyVault
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionState
import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.push.FakeFcmTokenProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountUseCaseTest {

    private lateinit var repository: FakeAccountRepository
    private lateinit var eraser: FakeLocalAccountDataEraser

    @Before
    fun setUp() {
        repository = FakeAccountRepository()
        eraser = FakeLocalAccountDataEraser()
    }

    private fun useCase() = DeleteAccountUseCase(repository, eraser)

    @Test
    fun `manda la contrasena tal cual al repositorio`() = runTest {
        useCase()("mi-clave")

        assertEquals(listOf("mi-clave"), repository.receivedPasswords)
    }

    @Test
    fun `ante Deleted devuelve Deleted y limpia lo local una vez`() = runTest {
        repository.result = DeleteAccountResult.Deleted

        val result = useCase()("mi-clave")

        assertEquals(DeleteAccountResult.Deleted, result)
        assertEquals(1, eraser.completedCount)
    }

    @Test
    fun `ante cualquier fallo no se borra nada local`() = runTest {
        listOf(
            DeleteAccountError.InvalidPassword,
            DeleteAccountError.SessionExpired,
            DeleteAccountError.RateLimited,
            DeleteAccountError.Unavailable,
            DeleteAccountError.Network,
            DeleteAccountError.Unexpected,
        ).forEach { error ->
            repository.result = DeleteAccountResult.Failed(error)

            val result = useCase()("mi-clave")

            assertEquals(DeleteAccountResult.Failed(error), result)
            assertEquals("error $error", 0, eraser.startedCount)
        }
    }

    /**
     * Con la cuenta ya borrada del lado del servidor, que el usuario salga de la pantalla a
     * mitad de la limpieza no puede dejar tokens ni claves de una cuenta que ya no existe.
     */
    @Test
    fun `la limpieza local no se cancela si quien llamo se cancela despues del 204`() = runTest {
        repository.result = DeleteAccountResult.Deleted
        val eraserGate = CompletableDeferred<Unit>()
        eraser.gate = eraserGate

        val job = launch(start = CoroutineStart.UNDISPATCHED) { useCase()("mi-clave") }
        assertEquals("la limpieza arrancó", 1, eraser.startedCount)

        job.cancel()
        eraserGate.complete(Unit)
        job.join()

        assertEquals("la limpieza terminó pese a la cancelación", 1, eraser.completedCount)
    }

    @Test
    fun `si se cancela antes del 204 no se limpia nada`() = runTest {
        repository.gate = CompletableDeferred<Unit>()

        val job = launch(start = CoroutineStart.UNDISPATCHED) { useCase()("mi-clave") }
        job.cancel()
        job.join()

        assertEquals(0, eraser.startedCount)
    }

    /** Las piezas reales de la limpieza, sólo el Keystore y Firebase sustituidos. */
    @Test
    fun `ante un 204 la limpieza local es completa`() = runTest {
        val vault = FakeSessionKeyVault()
        val sessionPrefs = FakeSharedPreferences()
        val tokenStore = SecureTokenStore(sessionPrefs, vault)
        tokenStore.startSession(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
        assertTrue(tokenStore.enableBiometric())
        val fcmTokenProvider = FakeFcmTokenProvider()
        val pendingStore = PendingFcmTokenStore(FakeSharedPreferences())
        pendingStore.savePendingRegistration("fcm-por-registrar")
        pendingStore.savePendingDeletion("fcm-por-borrar")
        val realEraser = LocalAccountDataEraserImpl(
            sessionManager = SessionManager(FakeRefreshApi(), tokenStore, UnconfinedTestDispatcher()),
            fcmTokenProvider = fcmTokenProvider,
            pendingFcmTokenStore = pendingStore,
        )
        repository.result = DeleteAccountResult.Deleted

        DeleteAccountUseCase(repository, realEraser)("mi-clave")

        assertNull("tokens de sesión", tokenStore.read())
        assertEquals("sessionState", SessionState.NONE, tokenStore.sessionState.value)
        assertNull("blob biométrico", tokenStore.readWrappedRefresh())
        assertFalse("clave de Keystore", vault.hasKey)
        assertEquals("token FCM invalidado", 1, fcmTokenProvider.deleteCallCount)
        assertNull("alta FCM pendiente", pendingStore.readPendingRegistration())
        assertNull("baja FCM pendiente", pendingStore.readPendingDeletion())
    }

    @Test
    fun `ante un fallo la sesion, las claves y los tokens FCM quedan intactos`() = runTest {
        val vault = FakeSessionKeyVault()
        val tokenStore = SecureTokenStore(FakeSharedPreferences(), vault)
        tokenStore.startSession(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
        assertTrue(tokenStore.enableBiometric())
        val fcmTokenProvider = FakeFcmTokenProvider()
        val pendingStore = PendingFcmTokenStore(FakeSharedPreferences())
        pendingStore.savePendingRegistration("fcm-por-registrar")
        val realEraser = LocalAccountDataEraserImpl(
            sessionManager = SessionManager(FakeRefreshApi(), tokenStore, UnconfinedTestDispatcher()),
            fcmTokenProvider = fcmTokenProvider,
            pendingFcmTokenStore = pendingStore,
        )
        repository.result = DeleteAccountResult.Failed(DeleteAccountError.InvalidPassword)

        DeleteAccountUseCase(repository, realEraser)("mala")

        assertNotNull("tokens de sesión", tokenStore.read())
        assertTrue("clave de Keystore", vault.hasKey)
        assertEquals("token FCM", 0, fcmTokenProvider.deleteCallCount)
        assertEquals("alta FCM pendiente", "fcm-por-registrar", pendingStore.readPendingRegistration())
    }
}
