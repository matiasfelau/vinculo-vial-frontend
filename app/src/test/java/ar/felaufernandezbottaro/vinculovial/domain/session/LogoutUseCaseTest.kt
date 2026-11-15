package ar.felaufernandezbottaro.vinculovial.domain.session

import ar.felaufernandezbottaro.vinculovial.data.push.PendingFcmTokenStore
import ar.felaufernandezbottaro.vinculovial.data.push.TokenSyncManager
import ar.felaufernandezbottaro.vinculovial.data.session.FakeRefreshApi
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSessionKeyVault
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.push.DeleteFcmTokenError
import ar.felaufernandezbottaro.vinculovial.domain.push.DeleteFcmTokenResult
import ar.felaufernandezbottaro.vinculovial.domain.push.DeleteFcmTokenUseCase
import ar.felaufernandezbottaro.vinculovial.domain.push.FakeFcmTokenProvider
import ar.felaufernandezbottaro.vinculovial.domain.push.FakePushTokenRepository
import ar.felaufernandezbottaro.vinculovial.domain.push.SyncFcmTokenUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * `SessionManagerTest` ya cubre en detalle el mapeo de respuestas de `/logout`; esto sólo
 * confirma que el caso de uso es un pasamanos fiel hacia `SessionManager.logout()`, incluida
 * la garantía central del contrato: limpieza local aun cuando la llamada de red falla. Los
 * casos de la baja del token FCM (ver CLAUDE.md, US-06) viven acá también, porque orquestarla
 * antes de limpiar la sesión es responsabilidad de este mismo caso de uso.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogoutUseCaseTest {

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var refreshApi: FakeRefreshApi
    private lateinit var pushRepository: FakePushTokenRepository
    private lateinit var fcmTokenProvider: FakeFcmTokenProvider
    private lateinit var pendingStore: PendingFcmTokenStore

    private val tokens = SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz")

    @Before
    fun setUp() {
        tokenStore = SecureTokenStore(FakeSharedPreferences(), FakeSessionKeyVault())
        refreshApi = FakeRefreshApi()
        pushRepository = FakePushTokenRepository()
        fcmTokenProvider = FakeFcmTokenProvider()
        pendingStore = PendingFcmTokenStore(FakeSharedPreferences())
    }

    private fun logoutUseCase() = LogoutUseCase(
        sessionManager = SessionManager(refreshApi = refreshApi, tokenStore = tokenStore, dispatcher = UnconfinedTestDispatcher()),
        fcmTokenProvider = fcmTokenProvider,
        deleteFcmToken = DeleteFcmTokenUseCase(pushRepository),
        tokenSyncManager = TokenSyncManager(
            tokenStore = tokenStore,
            pendingStore = pendingStore,
            syncFcmToken = SyncFcmTokenUseCase(pushRepository),
            deleteFcmToken = DeleteFcmTokenUseCase(pushRepository),
            fcmTokenProvider = fcmTokenProvider,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
        ),
    )

    @Test
    fun `logout exitoso borra la sesion local`() = runTest {
        tokenStore.startSession(tokens)
        val logout = logoutUseCase()

        logout()

        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `logout borra la sesion local aun cuando la llamada de red falla`() = runTest {
        tokenStore.startSession(tokens)
        refreshApi.logoutExceptionToThrow = IOException("sin conexion")
        val logout = logoutUseCase()

        logout()

        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `un logout exitoso da de baja el token FCM antes de limpiar la sesion`() = runTest {
        tokenStore.startSession(tokens)
        fcmTokenProvider.token = "fcm-a-borrar"
        val logout = logoutUseCase()

        logout()

        assertEquals(listOf("fcm-a-borrar"), pushRepository.deletedTokens)
    }

    @Test
    fun `sin token FCM disponible el logout no intenta darlo de baja`() = runTest {
        tokenStore.startSession(tokens)
        fcmTokenProvider.token = null
        val logout = logoutUseCase()

        logout()

        assertEquals(emptyList<String>(), pushRepository.deletedTokens)
        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `un fallo al dar de baja el token FCM no bloquea el logout`() = runTest {
        tokenStore.startSession(tokens)
        pushRepository.deleteResult = DeleteFcmTokenResult.Failed(DeleteFcmTokenError.Network)
        val logout = logoutUseCase()

        logout()

        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `un fallo al dar de baja el token FCM lo deja pendiente para reintentar`() = runTest {
        tokenStore.startSession(tokens)
        pushRepository.deleteResult = DeleteFcmTokenResult.Failed(DeleteFcmTokenError.Network)
        fcmTokenProvider.token = "fcm-a-borrar"
        val logout = logoutUseCase()

        logout()

        assertEquals("fcm-a-borrar", pendingStore.readPendingDeletion())
    }

    @Test
    fun `una baja exitosa no deja nada pendiente`() = runTest {
        tokenStore.startSession(tokens)
        fcmTokenProvider.token = "fcm-a-borrar"
        val logout = logoutUseCase()

        logout()

        assertNull(pendingStore.readPendingDeletion())
    }
}
