package ar.felaufernandezbottaro.vinculovial.data.session

import ar.felaufernandezbottaro.vinculovial.data.remote.auth.RefreshApi
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshResponseDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshTokenRequestDto
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Cubre el contrato central de US-03: bajo refresh concurrente, exactamente una corrutina
 * llama a `/refresh` y ninguna reenvía un refresh token ya rotado (Double-Checked Locking).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var refreshApi: FakeRefreshApi

    private val tokenViejo = SessionTokens(accessToken = "access-viejo", refreshToken = "refresh-viejo")
    private val tokenNuevo = SessionTokens(accessToken = "access-nuevo", refreshToken = "refresh-nuevo")

    @Before
    fun setUp() {
        AeadConfig.register()
        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        refreshApi = FakeRefreshApi()
    }

    private fun manager() = SessionManager(
        refreshApi = refreshApi,
        tokenStore = tokenStore,
        dispatcher = UnconfinedTestDispatcher(),
    )

    private fun errorResponse(code: Int) =
        Response.error<RefreshResponseDto>(code, "".toResponseBody("application/json".toMediaType()))

    @Test
    fun `N corrutinas concurrentes disparan una unica llamada a refresh`() = runTest {
        tokenStore.save(tokenViejo)
        val manager = manager()

        val results = (1..20).map {
            async { manager.refreshSession(failedAccessToken = tokenViejo.accessToken) }
        }.awaitAll()

        assertEquals(1, refreshApi.callCount)
        assertTrue(results.all { it == SessionRefreshOutcome.Renewed(tokenNuevo) })
    }

    @Test
    fun `ninguna corrutina reenvia el refresh token ya rotado`() = runTest {
        tokenStore.save(tokenViejo)
        val manager = manager()

        (1..20).map { async { manager.refreshSession(failedAccessToken = tokenViejo.accessToken) } }.awaitAll()

        assertEquals(listOf(tokenViejo.refreshToken), refreshApi.receivedRefreshTokens)
    }

    @Test
    fun `el 200 deja el par nuevo persistido`() = runTest {
        tokenStore.save(tokenViejo)

        manager().refreshSession(failedAccessToken = tokenViejo.accessToken)

        assertEquals(tokenNuevo, tokenStore.read())
    }

    @Test
    fun `una corrutina cuyo token ya no es el actual no llama a refresh`() = runTest {
        tokenStore.save(SessionTokens(accessToken = "access-actual", refreshToken = "refresh-actual"))

        val result = manager().refreshSession(failedAccessToken = "access-ya-rotado")

        assertEquals(0, refreshApi.callCount)
        assertEquals(
            SessionRefreshOutcome.Renewed(SessionTokens(accessToken = "access-actual", refreshToken = "refresh-actual")),
            result,
        )
    }

    @Test
    fun `sin sesion guardada no llama a refresh y devuelve logged out`() = runTest {
        val result = manager().refreshSession(failedAccessToken = "cualquiera")

        assertEquals(0, refreshApi.callCount)
        assertEquals(SessionRefreshOutcome.LoggedOut, result)
    }

    @Test
    fun `401 limpia la sesion local`() = runTest {
        tokenStore.save(tokenViejo)
        refreshApi.response = errorResponse(401)

        val result = manager().refreshSession(failedAccessToken = tokenViejo.accessToken)

        assertEquals(SessionRefreshOutcome.LoggedOut, result)
        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `403 limpia la sesion local`() = runTest {
        tokenStore.save(tokenViejo)
        refreshApi.response = errorResponse(403)

        val result = manager().refreshSession(failedAccessToken = tokenViejo.accessToken)

        assertEquals(SessionRefreshOutcome.LoggedOut, result)
        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `un fallo de red no limpia la sesion local`() = runTest {
        tokenStore.save(tokenViejo)
        refreshApi.exceptionToThrow = IOException("sin conexion")

        val result = manager().refreshSession(failedAccessToken = tokenViejo.accessToken)

        assertEquals(SessionRefreshOutcome.Unavailable, result)
        assertEquals(tokenViejo, tokenStore.read())
    }

    @Test
    fun `500 no limpia la sesion local`() = runTest {
        tokenStore.save(tokenViejo)
        refreshApi.response = errorResponse(500)

        val result = manager().refreshSession(failedAccessToken = tokenViejo.accessToken)

        assertEquals(SessionRefreshOutcome.Unavailable, result)
        assertEquals(tokenViejo, tokenStore.read())
    }

    @Test
    fun `clearSession borra la sesion sin llamar a la red`() = runTest {
        tokenStore.save(tokenViejo)

        manager().clearSession()

        assertEquals(null, tokenStore.read())
        assertEquals(0, refreshApi.callCount)
    }

    @Test
    fun `logout llama al endpoint con el refresh token y limpia la sesion`() = runTest {
        tokenStore.save(tokenViejo)

        manager().logout()

        assertEquals(listOf(tokenViejo.refreshToken), refreshApi.receivedLogoutTokens)
        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `logout no llama a refresh`() = runTest {
        tokenStore.save(tokenViejo)

        manager().logout()

        assertEquals(0, refreshApi.callCount)
    }

    @Test
    fun `logout limpia la sesion aun cuando la llamada de red falla`() = runTest {
        tokenStore.save(tokenViejo)
        refreshApi.logoutExceptionToThrow = IOException("sin conexion")

        manager().logout()

        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `logout limpia la sesion aun cuando el backend responde con error`() = runTest {
        tokenStore.save(tokenViejo)
        refreshApi.logoutResponse = Response.error(500, "".toResponseBody("application/json".toMediaType()))

        manager().logout()

        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `logout sin sesion guardada no llama a la red`() = runTest {
        manager().logout()

        assertEquals(0, refreshApi.logoutCallCount)
    }

    @Test
    fun `clearSessionForAccountDeletion borra los tokens sin llamar a la red`() = runTest {
        tokenStore.save(tokenViejo)

        manager().clearSessionForAccountDeletion { }

        assertEquals(null, tokenStore.read())
        assertEquals(0, refreshApi.callCount)
        assertEquals(0, refreshApi.logoutCallCount)
    }

    @Test
    fun `clearSessionForAccountDeletion corre afterClear con los tokens ya borrados`() = runTest {
        tokenStore.save(tokenViejo)
        var sessionWhenAfterClearRan: SessionTokens? = tokenViejo

        manager().clearSessionForAccountDeletion { sessionWhenAfterClearRan = tokenStore.read() }

        assertEquals(null, sessionWhenAfterClearRan)
    }

    @Test
    fun `clearSessionForAccountDeletion avisa por sessionState que ya no hay sesion`() = runTest {
        tokenStore.save(tokenViejo)

        manager().clearSessionForAccountDeletion { }

        assertEquals(false, tokenStore.sessionState.value)
    }

    @Test
    fun `si afterClear falla los tokens igual quedaron borrados`() = runTest {
        tokenStore.save(tokenViejo)

        val failure = try {
            manager().clearSessionForAccountDeletion { throw IllegalStateException("Keystore roto") }
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertEquals("Keystore roto", failure?.message)
        assertEquals(null, tokenStore.read())
    }

    /**
     * La razón de tomar el Mutex: sin él, un refresh en vuelo podía guardar un par recién rotado
     * *después* del borrado y resucitar la sesión de una cuenta que ya no existe.
     */
    @Test
    fun `clearSessionForAccountDeletion espera a un refresh en vuelo y la sesion no resucita`() = runTest {
        tokenStore.save(tokenViejo)
        val gate = CompletableDeferred<Unit>()
        val gatedApi = object : RefreshApi {
            override suspend fun refresh(body: RefreshTokenRequestDto): Response<RefreshResponseDto> {
                gate.await()
                return Response.success(RefreshResponseDto(accessToken = "access-nuevo", refreshToken = "refresh-nuevo"))
            }

            override suspend fun logout(body: RefreshTokenRequestDto): Response<Unit> = Response.success(Unit)
        }
        val manager = SessionManager(gatedApi, tokenStore, UnconfinedTestDispatcher())

        // UNDISPATCHED: cada una corre ya hasta su primera suspensión. Sin esto, el dispatcher
        // de runTest las encola y el orden lo decidiría el scheduler, no el Mutex.
        val refresh = async(start = CoroutineStart.UNDISPATCHED) {
            manager.refreshSession(failedAccessToken = tokenViejo.accessToken)
        }
        val deletion = async(start = CoroutineStart.UNDISPATCHED) { manager.clearSessionForAccountDeletion { } }

        assertFalse("el borrado tiene que esperar al refresh", deletion.isCompleted)

        gate.complete(Unit)
        refresh.await()
        deletion.await()

        assertEquals(null, tokenStore.read())
    }
}
