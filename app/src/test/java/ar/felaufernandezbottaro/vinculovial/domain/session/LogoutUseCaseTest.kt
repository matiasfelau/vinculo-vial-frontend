package ar.felaufernandezbottaro.vinculovial.domain.session

import ar.felaufernandezbottaro.vinculovial.data.session.FakeRefreshApi
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * `SessionManagerTest` ya cubre en detalle el mapeo de respuestas de `/logout`; esto sólo
 * confirma que el caso de uso es un pasamanos fiel hacia `SessionManager.logout()`, incluida
 * la garantía central del contrato: limpieza local aun cuando la llamada de red falla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogoutUseCaseTest {

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var refreshApi: FakeRefreshApi

    private val tokens = SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz")

    @Before
    fun setUp() {
        AeadConfig.register()
        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        refreshApi = FakeRefreshApi()
    }

    private fun logoutUseCase() = LogoutUseCase(
        SessionManager(refreshApi = refreshApi, tokenStore = tokenStore, dispatcher = UnconfinedTestDispatcher()),
    )

    @Test
    fun `logout exitoso borra la sesion local`() = runTest {
        tokenStore.save(tokens)
        val logout = logoutUseCase()

        logout()

        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `logout borra la sesion local aun cuando la llamada de red falla`() = runTest {
        tokenStore.save(tokens)
        refreshApi.logoutExceptionToThrow = IOException("sin conexion")
        val logout = logoutUseCase()

        logout()

        assertEquals(null, tokenStore.read())
    }
}
