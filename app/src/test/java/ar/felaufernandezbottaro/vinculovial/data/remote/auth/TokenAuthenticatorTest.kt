package ar.felaufernandezbottaro.vinculovial.data.remote.auth

import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshResponseDto
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response as RetrofitResponse

private fun errorResponse(code: Int): RetrofitResponse<RefreshResponseDto> =
    RetrofitResponse.error(code, "".toResponseBody("application/json".toMediaType()))

/**
 * `authenticate()` es sincrónico (contrato de OkHttp), así que se ejercita directo -- sin
 * MockWebServer -- armando Request/Response crudos. [SessionManager] participa real, con
 * [FakeRefreshApi] y un `SecureTokenStore` en memoria, para no duplicar el mapeo de
 * resultados que ya cubre `SessionManagerTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TokenAuthenticatorTest {

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var refreshApi: FakeRefreshApi
    private lateinit var authenticator: TokenAuthenticator

    @Before
    fun setUp() {
        AeadConfig.register()
        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        refreshApi = FakeRefreshApi()
        authenticator = TokenAuthenticator(SessionManager(refreshApi, tokenStore, UnconfinedTestDispatcher()))
    }

    private fun requestWithBearer(token: String?): Request =
        Request.Builder()
            .url("http://localhost/api/v1/protegido")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()

    private fun unauthorizedResponseTo(request: Request, prior: Response? = null): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody("application/json".toMediaType()))
            .priorResponse(prior)
            .build()

    @Test
    fun `401 con refresh exitoso reintenta con el token nuevo`() {
        tokenStore.save(SessionTokens(accessToken = "access-viejo", refreshToken = "refresh-viejo"))
        val request = requestWithBearer("access-viejo")

        val retried = authenticator.authenticate(null, unauthorizedResponseTo(request))

        assertEquals("Bearer access-nuevo", retried?.header("Authorization"))
        assertEquals(1, refreshApi.callCount)
    }

    @Test
    fun `un refresh rechazado con 403 no reintenta y limpia la sesion`() {
        tokenStore.save(SessionTokens(accessToken = "access-viejo", refreshToken = "refresh-viejo"))
        refreshApi.response = errorResponse(403)
        val request = requestWithBearer("access-viejo")

        val retried = authenticator.authenticate(null, unauthorizedResponseTo(request))

        assertNull(retried)
        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `sin sesion guardada no reintenta`() {
        val request = requestWithBearer("access-viejo")

        val retried = authenticator.authenticate(null, unauthorizedResponseTo(request))

        assertNull(retried)
    }

    @Test
    fun `una request sin Authorization no dispara ningun refresh`() {
        val request = requestWithBearer(token = null)

        val retried = authenticator.authenticate(null, unauthorizedResponseTo(request))

        assertNull(retried)
        assertEquals(0, refreshApi.callCount)
    }

    @Test
    fun `no reintenta una segunda vez si el reintento tambien vuelve 401`() {
        tokenStore.save(SessionTokens(accessToken = "access-viejo", refreshToken = "refresh-viejo"))
        // OkHttp exige que un priorResponse no tenga cuerpo (se descarta al encadenarlo).
        val firstResponse = unauthorizedResponseTo(requestWithBearer("access-viejo")).newBuilder().body(null).build()
        val secondResponse = unauthorizedResponseTo(requestWithBearer("access-nuevo"), prior = firstResponse)

        val retried = authenticator.authenticate(null, secondResponse)

        assertNull(retried)
        assertEquals(0, refreshApi.callCount)
    }
}
