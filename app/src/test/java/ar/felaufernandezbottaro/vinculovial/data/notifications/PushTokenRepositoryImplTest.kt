package ar.felaufernandezbottaro.vinculovial.data.notifications

import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.push.DeleteFcmTokenError
import ar.felaufernandezbottaro.vinculovial.domain.push.DeleteFcmTokenResult
import ar.felaufernandezbottaro.vinculovial.domain.push.SyncFcmTokenError
import ar.felaufernandezbottaro.vinculovial.domain.push.SyncFcmTokenResult
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenRepositoryImplTest {

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var api: FakeNotificationsApi

    @Before
    fun setUp() {
        AeadConfig.register()
        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        tokenStore.save(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
        api = FakeNotificationsApi()
    }

    private fun repository() = PushTokenRepositoryImpl(
        api = api,
        tokenStore = tokenStore,
        dispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `registrar el token manda el access token como bearer y el fcm_token en el cuerpo`() = runTest {
        repository().registerToken("fcm-token-1")

        assertEquals("Bearer access-abc", api.lastRegisterAuthorization)
        assertEquals("fcm-token-1", api.lastRegisterToken)
    }

    @Test
    fun `registrar el token exitoso es Success`() = runTest {
        val result = repository().registerToken("fcm-token-1")

        assertEquals(SyncFcmTokenResult.Success, result)
    }

    @Test
    fun `429 al registrar es RateLimited`() = runTest {
        api.registerResponse = errorResponse(429)

        val result = repository().registerToken("fcm-token-1")

        assertEquals(SyncFcmTokenResult.Failed(SyncFcmTokenError.RateLimited), result)
    }

    @Test
    fun `503 al registrar es Unavailable, transitorio`() = runTest {
        api.registerResponse = errorResponse(503)

        val result = repository().registerToken("fcm-token-1")

        assertEquals(SyncFcmTokenResult.Failed(SyncFcmTokenError.Unavailable), result)
    }

    @Test
    fun `500 al registrar es un error inesperado`() = runTest {
        api.registerResponse = errorResponse(500)

        val result = repository().registerToken("fcm-token-1")

        assertEquals(SyncFcmTokenResult.Failed(SyncFcmTokenError.Unexpected), result)
    }

    @Test
    fun `un fallo de red al registrar es Network`() = runTest {
        api.registerExceptionToThrow = IOException("sin conexion")

        val result = repository().registerToken("fcm-token-1")

        assertEquals(SyncFcmTokenResult.Failed(SyncFcmTokenError.Network), result)
    }

    @Test
    fun `sin sesion guardada no registra el token por red`() = runTest {
        tokenStore.clear()

        val result = repository().registerToken("fcm-token-1")

        assertEquals(SyncFcmTokenResult.Failed(SyncFcmTokenError.Unexpected), result)
        assertEquals(0, api.registerCallCount)
    }

    @Test
    fun `borrar el token manda el access token como bearer y el fcm_token en el cuerpo`() = runTest {
        repository().deleteToken("fcm-token-1")

        assertEquals("Bearer access-abc", api.lastDeleteAuthorization)
        assertEquals("fcm-token-1", api.lastDeleteToken)
    }

    @Test
    fun `borrar el token exitoso es Success`() = runTest {
        val result = repository().deleteToken("fcm-token-1")

        assertEquals(DeleteFcmTokenResult.Success, result)
    }

    @Test
    fun `503 al borrar es Unavailable, transitorio`() = runTest {
        api.deleteResponse = errorResponse(503)

        val result = repository().deleteToken("fcm-token-1")

        assertEquals(DeleteFcmTokenResult.Failed(DeleteFcmTokenError.Unavailable), result)
    }

    @Test
    fun `500 al borrar es un error inesperado`() = runTest {
        api.deleteResponse = errorResponse(500)

        val result = repository().deleteToken("fcm-token-1")

        assertEquals(DeleteFcmTokenResult.Failed(DeleteFcmTokenError.Unexpected), result)
    }

    @Test
    fun `un fallo de red al borrar es Network y no bloquea nada por si mismo`() = runTest {
        api.deleteExceptionToThrow = IOException("sin conexion")

        val result = repository().deleteToken("fcm-token-1")

        assertEquals(DeleteFcmTokenResult.Failed(DeleteFcmTokenError.Network), result)
    }

    @Test
    fun `sin sesion guardada no borra el token por red`() = runTest {
        tokenStore.clear()

        val result = repository().deleteToken("fcm-token-1")

        assertEquals(DeleteFcmTokenResult.Failed(DeleteFcmTokenError.Unexpected), result)
        assertEquals(0, api.deleteCallCount)
    }

    private fun errorResponse(code: Int) =
        Response.error<Unit>(code, "".toResponseBody("application/json".toMediaType()))
}
