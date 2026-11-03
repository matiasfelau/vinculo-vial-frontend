package ar.felaufernandezbottaro.vinculovial.data.account

import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountError
import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountResult
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Mapeo de códigos de `DELETE /api/v1/users/me` (US-11) a resultado de dominio. Lo que importa
 * es el `code` del 401: `INVALID_PASSWORD` (Identity) no es lo mismo que una sesión vencida (Kong).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountRepositoryImplTest {

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var api: FakeAccountApi

    @Before
    fun setUp() {
        AeadConfig.register()
        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        tokenStore.save(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
        api = FakeAccountApi()
    }

    private fun repository() = AccountRepositoryImpl(
        api = api,
        tokenStore = tokenStore,
        dispatcher = UnconfinedTestDispatcher(),
        moshi = Moshi.Builder().build(),
    )

    private fun failed(error: DeleteAccountError) = DeleteAccountResult.Failed(error)

    private fun errorResponse(code: Int, body: String = "") =
        Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType()))

    @Test
    fun `manda el access token como bearer y la contrasena en el body`() = runTest {
        repository().deleteAccount("mi-clave")

        assertEquals("Bearer access-abc", api.lastAuthorization)
        assertEquals("mi-clave", api.lastBody?.password)
    }

    @Test
    fun `204 es Deleted`() = runTest {
        api.response = Response.success(204, Unit)

        assertEquals(DeleteAccountResult.Deleted, repository().deleteAccount("mi-clave"))
    }

    @Test
    fun `el repositorio no toca la sesion local ni ante un 204`() = runTest {
        api.response = Response.success(204, Unit)

        repository().deleteAccount("mi-clave")

        // Borrar lo local es del caso de uso, no de quien habla con el backend.
        assertEquals("access-abc", tokenStore.read()?.accessToken)
    }

    @Test
    fun `401 INVALID_PASSWORD es InvalidPassword`() = runTest {
        api.response = errorResponse(401, """{"error":"contraseña incorrecta","code":"INVALID_PASSWORD"}""")

        assertEquals(failed(DeleteAccountError.InvalidPassword), repository().deleteAccount("mala"))
    }

    @Test
    fun `401 INVALID_PASSWORD deja la sesion intacta`() = runTest {
        api.response = errorResponse(401, """{"error":"contraseña incorrecta","code":"INVALID_PASSWORD"}""")

        repository().deleteAccount("mala")

        assertEquals("access-abc", tokenStore.read()?.accessToken)
    }

    @Test
    fun `401 sin code con la sesion ya borrada por el refresh es SessionExpired`() = runTest {
        api.response = errorResponse(401, """{"message":"Unauthorized"}""")
        // Lo que hace el TokenAuthenticator cuando el refresh es rechazado: borra la sesión y
        // deja pasar el 401 original.
        api.onCall = { tokenStore.clear() }

        assertEquals(failed(DeleteAccountError.SessionExpired), repository().deleteAccount("mi-clave"))
    }

    @Test
    fun `401 sin code con la sesion todavia en pie es Unavailable, el refresh fallo por algo transitorio`() = runTest {
        api.response = errorResponse(401, """{"message":"Unauthorized"}""")

        assertEquals(failed(DeleteAccountError.Unavailable), repository().deleteAccount("mi-clave"))
    }

    @Test
    fun `401 con cuerpo vacio o ilegible se trata como sin code`() = runTest {
        listOf("", "no es json").forEach { body ->
            api.response = errorResponse(401, body)
            api.onCall = { tokenStore.clear() }
            tokenStore.save(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))

            assertEquals(
                "cuerpo '$body'",
                failed(DeleteAccountError.SessionExpired),
                repository().deleteAccount("mi-clave"),
            )
        }
    }

    @Test
    fun `401 con otro code no se confunde con INVALID_PASSWORD`() = runTest {
        api.response = errorResponse(401, """{"error":"x","code":"OTRO"}""")
        api.onCall = { tokenStore.clear() }

        assertEquals(failed(DeleteAccountError.SessionExpired), repository().deleteAccount("mi-clave"))
    }

    @Test
    fun `429 es RateLimited`() = runTest {
        api.response = errorResponse(429)

        assertEquals(failed(DeleteAccountError.RateLimited), repository().deleteAccount("mi-clave"))
    }

    @Test
    fun `502, 503 y 504 son Unavailable`() = runTest {
        listOf(502, 503, 504).forEach { status ->
            api.response = errorResponse(status, """{"error":"caido","code":"DEPENDENCY_UNAVAILABLE"}""")

            assertEquals("status $status", failed(DeleteAccountError.Unavailable), repository().deleteAccount("x"))
        }
    }

    @Test
    fun `400 INVALID_BODY, 403 y 500 son un error inesperado`() = runTest {
        listOf(400, 403, 500).forEach { status ->
            api.response = errorResponse(status, """{"error":"x","code":"INVALID_BODY"}""")

            assertEquals("status $status", failed(DeleteAccountError.Unexpected), repository().deleteAccount("x"))
        }
    }

    @Test
    fun `circuito abierto es Unavailable`() = runTest {
        api.exceptionToThrow = CircuitBreakerOpenException("users")

        assertEquals(failed(DeleteAccountError.Unavailable), repository().deleteAccount("mi-clave"))
    }

    @Test
    fun `un fallo de red es Network`() = runTest {
        api.exceptionToThrow = IOException("sin conexion")

        assertEquals(failed(DeleteAccountError.Network), repository().deleteAccount("mi-clave"))
    }

    @Test
    fun `una excepcion inesperada es Unexpected`() = runTest {
        api.exceptionToThrow = IllegalStateException("boom")

        assertEquals(failed(DeleteAccountError.Unexpected), repository().deleteAccount("mi-clave"))
    }

    @Test
    fun `la cancelacion se propaga, no se traduce a un error`() = runTest {
        api.exceptionToThrow = CancellationException("la pantalla se cerro")

        val thrown = try {
            repository().deleteAccount("mi-clave")
            null
        } catch (e: CancellationException) {
            e
        }

        assertNotNull(thrown)
    }

    @Test
    fun `sin sesion guardada es SessionExpired y no llama a la red`() = runTest {
        tokenStore.clear()

        val result = repository().deleteAccount("mi-clave")

        assertEquals(failed(DeleteAccountError.SessionExpired), result)
        assertEquals(0, api.callCount)
    }
}
