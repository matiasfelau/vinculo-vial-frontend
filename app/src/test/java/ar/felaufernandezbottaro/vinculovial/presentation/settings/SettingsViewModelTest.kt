package ar.felaufernandezbottaro.vinculovial.presentation.settings

import ar.felaufernandezbottaro.vinculovial.data.push.PendingFcmTokenStore
import ar.felaufernandezbottaro.vinculovial.data.push.TokenSyncManager
import ar.felaufernandezbottaro.vinculovial.data.session.FakeBiometricCapability
import ar.felaufernandezbottaro.vinculovial.data.session.FakeRefreshApi
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSessionKeyVault
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.data.session.SessionState
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.push.DeleteFcmTokenUseCase
import ar.felaufernandezbottaro.vinculovial.domain.push.FakeFcmTokenProvider
import ar.felaufernandezbottaro.vinculovial.domain.push.FakePushTokenRepository
import ar.felaufernandezbottaro.vinculovial.domain.push.SyncFcmTokenUseCase
import ar.felaufernandezbottaro.vinculovial.domain.session.BiometricUnlockSettings
import ar.felaufernandezbottaro.vinculovial.domain.session.LogoutUseCase
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var vault: FakeSessionKeyVault
    private lateinit var capability: FakeBiometricCapability
    private lateinit var refreshApi: FakeRefreshApi

    @Before
    fun setUp() {
        vault = FakeSessionKeyVault()
        capability = FakeBiometricCapability(strongEnrolled = true)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), vault)
        refreshApi = FakeRefreshApi()
        tokenStore.startSession(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
    }

    private fun viewModel(): SettingsViewModel {
        val pushRepository = FakePushTokenRepository()
        val fcmTokenProvider = FakeFcmTokenProvider()
        val sessionManager = SessionManager(
            refreshApi = refreshApi,
            tokenStore = tokenStore,
            dispatcher = UnconfinedTestDispatcher(),
        )
        val logoutUseCase = LogoutUseCase(
            sessionManager = sessionManager,
            fcmTokenProvider = fcmTokenProvider,
            deleteFcmToken = DeleteFcmTokenUseCase(pushRepository),
            tokenSyncManager = TokenSyncManager(
                tokenStore = tokenStore,
                pendingStore = PendingFcmTokenStore(FakeSharedPreferences()),
                syncFcmToken = SyncFcmTokenUseCase(pushRepository),
                deleteFcmToken = DeleteFcmTokenUseCase(pushRepository),
                fcmTokenProvider = fcmTokenProvider,
                scope = CoroutineScope(UnconfinedTestDispatcher()),
            ),
        )
        return SettingsViewModel(logoutUseCase, BiometricUnlockSettings(sessionManager, tokenStore, capability))
    }

    @Test
    fun `logout limpia la sesion`() = runTest {
        val viewModel = viewModel()

        viewModel.logout()

        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `un doble tap no dispara un segundo logout`() = runTest {
        val viewModel = viewModel()

        viewModel.logout()
        viewModel.logout()

        assertEquals(1, refreshApi.logoutCallCount)
    }

    @Test
    fun `isLoggingOut vuelve a false al terminar`() = runTest {
        val viewModel = viewModel()

        viewModel.logout()

        assertFalse(viewModel.uiState.value.isLoggingOut)
    }

    @Test
    fun `logout con la biometria activa borra tambien el blob y la clave`() = runTest {
        tokenStore.enableBiometric()
        val viewModel = viewModel()

        viewModel.logout()

        assertEquals(SessionState.NONE, tokenStore.sessionState.value)
        assertNull(tokenStore.readWrappedRefresh())
        assertFalse(vault.hasKey)
    }

    // --- Biometría (US-12) ---

    @Test
    fun `tras el login con biometria fuerte enrolada se ofrece activarla`() = runTest {
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.showBiometricOffer)
    }

    @Test
    fun `sin biometria fuerte enrolada no se ofrece ni se muestra el interruptor`() = runTest {
        capability.strongEnrolled = false

        val state = viewModel().uiState.value

        assertFalse(state.showBiometricOffer)
        assertFalse(state.isBiometricAvailable)
    }

    @Test
    fun `rechazar el ofrecimiento no lo repite`() = runTest {
        val viewModel = viewModel()

        viewModel.onBiometricOfferDismissed()

        assertFalse(viewModel.uiState.value.showBiometricOffer)
        assertFalse(viewModel.uiState.value.isBiometricEnabled)
    }

    @Test
    fun `aceptar el ofrecimiento activa la biometria y lo cierra`() = runTest {
        val viewModel = viewModel()

        viewModel.onBiometricToggle(true)

        val state = viewModel.uiState.value
        assertTrue(state.isBiometricEnabled)
        assertFalse(state.showBiometricOffer)
        assertTrue(tokenStore.biometricEnabled.value)
    }

    @Test
    fun `el interruptor apagado desactiva la biometria y borra blob y clave`() = runTest {
        val viewModel = viewModel()
        viewModel.onBiometricToggle(true)

        viewModel.onBiometricToggle(false)

        assertFalse(viewModel.uiState.value.isBiometricEnabled)
        assertNull(tokenStore.readWrappedRefresh())
        assertFalse(vault.hasKey)
        assertEquals(SessionState.UNLOCKED, tokenStore.sessionState.value)
    }

    @Test
    fun `con la biometria activada el interruptor se muestra aunque ya no haya huella enrolada`() = runTest {
        tokenStore.enableBiometric()
        capability.strongEnrolled = false

        val state = viewModel().uiState.value

        assertTrue(state.isBiometricEnabled)
        assertTrue(state.isBiometricAvailable)
    }

    @Test
    fun `si el dispositivo no puede crear la clave se avisa y no queda activada`() = runTest {
        vault.failOnCreate = true
        val viewModel = viewModel()

        viewModel.onBiometricToggle(true)

        val state = viewModel.uiState.value
        assertTrue(state.biometricActivationFailed)
        assertFalse(state.isBiometricEnabled)
        assertFalse("el ofrecimiento no se repite", state.showBiometricOffer)

        viewModel.onBiometricActivationFailureDismissed()
        assertFalse(viewModel.uiState.value.biometricActivationFailed)
    }
}
