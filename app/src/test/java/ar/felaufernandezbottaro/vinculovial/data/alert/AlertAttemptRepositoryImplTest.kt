package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.AlertAttemptResponseDto
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSessionKeyVault
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptResult
import com.google.crypto.tink.hybrid.HybridConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Instant

/**
 * Cubre la llamada 1 de US-08/US-10: sin body, sin patente, y cada código de respuesta del
 * Gateway mapea al resultado de dominio correcto. Un 404 ya no es "no está en la red".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertAttemptRepositoryImplTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun registerTink() {
            HybridConfig.register()
        }
    }

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var api: FakeAlertAttemptApi

    @Before
    fun setUp() {
        tokenStore = SecureTokenStore(FakeSharedPreferences(), FakeSessionKeyVault())
        tokenStore.startSession(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
        api = FakeAlertAttemptApi()
    }

    private fun repository() = AlertAttemptRepositoryImpl(
        api = api,
        tokenStore = tokenStore,
        dispatcher = UnconfinedTestDispatcher(),
        moshi = Moshi.Builder().build(),
    )

    @Test
    fun `manda el access token como bearer`() = runTest {
        repository().startAttempt()

        assertEquals("Bearer access-abc", api.lastAuthorization)
    }

    @Test
    fun `201 devuelve Started con el attempt_id y el vencimiento`() = runTest {
        api.response = Response.success(
            201,
            AlertAttemptResponseDto(attemptId = "attempt-1", expiresAt = "2030-01-01T00:10:00Z"),
        )

        val result = repository().startAttempt()

        assertEquals(
            StartAlertAttemptResult.Started(attemptId = "attempt-1", expiresAt = Instant.parse("2030-01-01T00:10:00Z")),
            result,
        )
    }

    @Test
    fun `201 acepta un expires_at RFC3339 con offset y fraccion de segundo`() = runTest {
        api.response = Response.success(
            201,
            AlertAttemptResponseDto(attemptId = "attempt-1", expiresAt = "2030-01-01T00:10:00.123456-03:00"),
        )

        val result = repository().startAttempt() as StartAlertAttemptResult.Started

        assertEquals(Instant.parse("2030-01-01T03:10:00.123456Z"), result.expiresAt)
    }

    @Test
    fun `201 con un expires_at ilegible es Unexpected`() = runTest {
        api.response = Response.success(
            201,
            AlertAttemptResponseDto(attemptId = "attempt-1", expiresAt = "manana"),
        )

        assertEquals(StartAlertAttemptResult.Unexpected, repository().startAttempt())
    }

    @Test
    fun `201 sin cuerpo es Unexpected`() = runTest {
        api.response = Response.success<AlertAttemptResponseDto>(201, null)

        assertEquals(StartAlertAttemptResult.Unexpected, repository().startAttempt())
    }

    @Test
    fun `403 ISSUER_UNDER_REVIEW es UnderReview`() = runTest {
        api.response = errorResponse(403, """{"error":"en revision","code":"ISSUER_UNDER_REVIEW"}""")

        assertEquals(StartAlertAttemptResult.UnderReview, repository().startAttempt())
    }

    @Test
    fun `403 con otro code es Unexpected`() = runTest {
        api.response = errorResponse(403, """{"error":"prohibido","code":"OTRO"}""")

        assertEquals(StartAlertAttemptResult.Unexpected, repository().startAttempt())
    }

    @Test
    fun `403 sin cuerpo parseable es Unexpected`() = runTest {
        api.response = errorResponse(403, "no es json")

        assertEquals(StartAlertAttemptResult.Unexpected, repository().startAttempt())
    }

    @Test
    fun `404 ya no significa que la patente no esta en la red, es un error inesperado`() = runTest {
        api.response = errorResponse(404, """{"error":"sin dueños","code":"NOT_IN_NETWORK"}""")

        assertEquals(StartAlertAttemptResult.Unexpected, repository().startAttempt())
    }

    @Test
    fun `429 es RateLimited`() = runTest {
        api.response = errorResponse(429)

        assertEquals(StartAlertAttemptResult.RateLimited, repository().startAttempt())
    }

    @Test
    fun `502, 503 y 504 son Unavailable`() = runTest {
        listOf(502, 503, 504).forEach { status ->
            api.response = errorResponse(status)

            assertEquals("status $status", StartAlertAttemptResult.Unavailable, repository().startAttempt())
        }
    }

    @Test
    fun `400, 500 y 401 son un error inesperado`() = runTest {
        listOf(400, 500, 401).forEach { status ->
            api.response = errorResponse(status)

            assertEquals("status $status", StartAlertAttemptResult.Unexpected, repository().startAttempt())
        }
    }

    @Test
    fun `circuito abierto es Unavailable`() = runTest {
        api.exceptionToThrow = CircuitBreakerOpenException("alerts")

        assertEquals(StartAlertAttemptResult.Unavailable, repository().startAttempt())
    }

    @Test
    fun `un fallo de red es Unavailable`() = runTest {
        api.exceptionToThrow = IOException("sin conexion")

        assertEquals(StartAlertAttemptResult.Unavailable, repository().startAttempt())
    }

    @Test
    fun `una excepcion inesperada es Unexpected`() = runTest {
        api.exceptionToThrow = IllegalStateException("boom")

        assertEquals(StartAlertAttemptResult.Unexpected, repository().startAttempt())
    }

    @Test
    fun `sin sesion guardada no llama a la red`() = runTest {
        tokenStore.clear()

        val result = repository().startAttempt()

        assertEquals(StartAlertAttemptResult.Unexpected, result)
        assertEquals(0, api.callCount)
    }

    private fun errorResponse(code: Int, body: String = "") =
        Response.error<AlertAttemptResponseDto>(code, body.toResponseBody("application/json".toMediaType()))
}
