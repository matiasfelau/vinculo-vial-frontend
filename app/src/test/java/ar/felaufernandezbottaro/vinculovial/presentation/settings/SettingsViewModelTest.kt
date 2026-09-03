package ar.felaufernandezbottaro.vinculovial.presentation.settings

import ar.felaufernandezbottaro.vinculovial.data.push.PendingFcmTokenStore
import ar.felaufernandezbottaro.vinculovial.data.push.TokenSyncManager
import ar.felaufernandezbottaro.vinculovial.data.session.FakeRefreshApi
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.push.DeleteFcmTokenUseCase
import ar.felaufernandezbottaro.vinculovial.domain.push.FakeFcmTokenProvider
import ar.felaufernandezbottaro.vinculovial.domain.push.FakePushTokenRepository
import ar.felaufernandezbottaro.vinculovial.domain.push.SyncFcmTokenUseCase
import ar.felaufernandezbottaro.vinculovial.domain.session.LogoutUseCase
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var refreshApi: FakeRefreshApi

    @Before
    fun setUp() {
        AeadConfig.register()
        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        refreshApi = FakeRefreshApi()
        tokenStore.save(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
    }

    private fun viewModel(): SettingsViewModel {
        val pushRepository = FakePushTokenRepository()
        val fcmTokenProvider = FakeFcmTokenProvider()
        val logoutUseCase = LogoutUseCase(
            sessionManager = SessionManager(refreshApi = refreshApi, tokenStore = tokenStore, dispatcher = UnconfinedTestDispatcher()),
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
        return SettingsViewModel(logoutUseCase)
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
}
