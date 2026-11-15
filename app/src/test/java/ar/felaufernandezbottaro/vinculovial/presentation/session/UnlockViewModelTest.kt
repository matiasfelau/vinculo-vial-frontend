package ar.felaufernandezbottaro.vinculovial.presentation.session

import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshResponseDto
import ar.felaufernandezbottaro.vinculovial.data.session.FakeRefreshApi
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSessionKeyVault
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.data.session.SessionState
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.session.LoginNotice
import ar.felaufernandezbottaro.vinculovial.domain.session.LoginNoticeStore
import ar.felaufernandezbottaro.vinculovial.domain.session.UnlockSessionUseCase
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import javax.crypto.Cipher

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var vault: FakeSessionKeyVault
    private lateinit var tokenStore: SecureTokenStore
    private lateinit var refreshApi: FakeRefreshApi
    private lateinit var loginNotice: LoginNoticeStore

    private val tokens = SessionTokens(accessToken = "access-viejo", refreshToken = "refresh-viejo")

    @Before
    fun setUp() {
        vault = FakeSessionKeyVault()
        tokenStore = SecureTokenStore(FakeSharedPreferences(), vault)
        refreshApi = FakeRefreshApi()
        loginNotice = LoginNoticeStore()
        tokenStore.startSession(tokens)
        tokenStore.enableBiometric()
        tokenStore.lock()
    }

    private fun viewModel() = UnlockViewModel(
        UnlockSessionUseCase(
            SessionManager(refreshApi, tokenStore, UnconfinedTestDispatcher()),
            loginNotice,
        ),
    )

    /** Muestra el prompt como lo haría la UI: entrega el `Cipher` ya autenticado de vuelta al ViewModel. */
    private fun UnlockViewModel.unlockWithFingerprint() {
        requestUnlock { cipher -> onAuthenticated(cipher) }
    }

    private fun errorResponse(code: Int) =
        Response.error<RefreshResponseDto>(code, "".toResponseBody("application/json".toMediaType()))

    @Test
    fun `con la huella correcta la sesion pasa a UNLOCKED`() = runTest {
        val viewModel = viewModel()

        viewModel.unlockWithFingerprint()

        assertEquals(SessionState.UNLOCKED, tokenStore.sessionState.value)
        assertNull(viewModel.uiState.value.failure)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `mientras el prompt esta abierto el estado es Authenticating`() = runTest {
        val viewModel = viewModel()
        var shown: Cipher? = null

        viewModel.requestUnlock { shown = it }

        assertNotNull(shown)
        assertEquals(UnlockStatus.Authenticating, viewModel.uiState.value.status)
    }

    @Test
    fun `un segundo toque con el prompt abierto no abre otro`() = runTest {
        val viewModel = viewModel()
        var prompts = 0

        viewModel.requestUnlock { prompts++ }
        viewModel.requestUnlock { prompts++ }

        assertEquals(1, prompts)
    }

    @Test
    fun `cancelar el prompt vuelve a Idle y deja reintentar`() = runTest {
        val viewModel = viewModel()
        viewModel.requestUnlock { }

        viewModel.onPromptFailed(PromptFailure.CANCELLED)

        assertEquals(UnlockStatus.Idle, viewModel.uiState.value.status)
        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
        var prompts = 0
        viewModel.requestUnlock { prompts++ }
        assertEquals(1, prompts)
    }

    @Test
    fun `demasiados intentos con la huella se informa`() = runTest {
        val viewModel = viewModel()
        viewModel.requestUnlock { }

        viewModel.onPromptFailed(PromptFailure.LOCKED_OUT)

        assertEquals(UnlockFailure.LOCKED_OUT, viewModel.uiState.value.failure)
    }

    @Test
    fun `otro error del prompt se informa y no toca la sesion`() = runTest {
        val viewModel = viewModel()
        viewModel.requestUnlock { }

        viewModel.onPromptFailed(PromptFailure.OTHER)

        assertEquals(UnlockFailure.BIOMETRIC_ERROR, viewModel.uiState.value.failure)
        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
    }

    @Test
    fun `sin red sigue en Desbloqueo con reintento y el blob intacto`() = runTest {
        refreshApi.exceptionToThrow = IOException("modo avion")
        val viewModel = viewModel()

        viewModel.unlockWithFingerprint()

        assertEquals(UnlockFailure.NETWORK, viewModel.uiState.value.failure)
        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
        assertNotNull(tokenStore.readWrappedRefresh())
        assertNull(loginNotice.notice.value)
    }

    @Test
    fun `al volver la red reintentar entra sin pedir la contrasena`() = runTest {
        refreshApi.exceptionToThrow = IOException("modo avion")
        val viewModel = viewModel()
        viewModel.unlockWithFingerprint()

        refreshApi.exceptionToThrow = null
        viewModel.unlockWithFingerprint()

        assertEquals(SessionState.UNLOCKED, tokenStore.sessionState.value)
        assertNull(viewModel.uiState.value.failure)
    }

    @Test
    fun `un 5xx del servidor tambien deja reintentar`() = runTest {
        refreshApi.response = errorResponse(503)
        val viewModel = viewModel()

        viewModel.unlockWithFingerprint()

        assertEquals(UnlockFailure.NETWORK, viewModel.uiState.value.failure)
        assertEquals(SessionState.LOCKED, tokenStore.sessionState.value)
    }

    @Test
    fun `un 401 deja NONE y avisa Tu sesion vencio`() = runTest {
        refreshApi.response = errorResponse(401)
        val viewModel = viewModel()

        viewModel.unlockWithFingerprint()

        assertEquals(SessionState.NONE, tokenStore.sessionState.value)
        assertEquals(LoginNotice.SESSION_EXPIRED, loginNotice.notice.value)
        assertNull(tokenStore.readWrappedRefresh())
        assertFalse(vault.hasKey)
    }

    @Test
    fun `si cambiaron las huellas deja NONE y avisa el cambio`() = runTest {
        vault.invalidated = true
        val viewModel = viewModel()
        var prompts = 0

        viewModel.requestUnlock { prompts++ }

        assertEquals(0, prompts)
        assertEquals(SessionState.NONE, tokenStore.sessionState.value)
        assertEquals(LoginNotice.BIOMETRICS_CHANGED, loginNotice.notice.value)
        assertNull(tokenStore.readWrappedRefresh())
        assertFalse(vault.hasKey)
        assertTrue(refreshApi.callCount == 0)
    }
}
