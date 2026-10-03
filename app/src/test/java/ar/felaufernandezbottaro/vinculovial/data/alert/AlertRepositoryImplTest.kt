package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertResult
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

/**
 * Cubre el requerimiento central de US-05: `sendAlert` publica el `pat_enc` que recibe tal
 * cual, sin volver a cifrar -- ver `SendAlertUseCaseTest` para la prueba de que ese `pat_enc`
 * es efectivamente el mismo que ya devolvió el check.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertRepositoryImplTest {

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var api: FakeAlertApi

    private val patEnc = "pat-enc-ya-generado-por-el-check"

    @Before
    fun setUp() {
        AeadConfig.register()
        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        tokenStore.save(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
        api = FakeAlertApi()
    }

    private fun repository() = AlertRepositoryImpl(
        api = api,
        tokenStore = tokenStore,
        dispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `manda el pat_enc recibido tal cual, sin transformarlo`() = runTest {
        repository().sendAlert(AlertType.CRASHED, patEnc)

        assertEquals(patEnc, api.lastRequest!!.patEnc)
    }

    @Test
    fun `manda el wireValue del catalogo cerrado como alert_type, no la etiqueta visible`() = runTest {
        repository().sendAlert(AlertType.PARKED_TOO_CLOSE, patEnc)

        assertEquals("PARKED_TOO_CLOSE", api.lastRequest!!.alertType)
    }

    @Test
    fun `manda el access token como bearer`() = runTest {
        repository().sendAlert(AlertType.CRASHED, patEnc)

        assertEquals("Bearer access-abc", api.lastAuthorization)
    }

    @Test
    fun `202 es Sent`() = runTest {
        api.response = Response.success(Unit)

        val result = repository().sendAlert(AlertType.CRASHED, patEnc)

        assertEquals(SendAlertResult.Sent, result)
    }

    @Test
    fun `400 es InvalidData`() = runTest {
        api.response = errorResponse(400)

        val result = repository().sendAlert(AlertType.CRASHED, patEnc)

        assertEquals(SendAlertResult.Failed(SendAlertError.InvalidData), result)
    }

    @Test
    fun `429 es RateLimited`() = runTest {
        api.response = errorResponse(429)

        val result = repository().sendAlert(AlertType.CRASHED, patEnc)

        assertEquals(SendAlertResult.Failed(SendAlertError.RateLimited), result)
    }

    @Test
    fun `503 es Unavailable, transitorio`() = runTest {
        api.response = errorResponse(503)

        val result = repository().sendAlert(AlertType.CRASHED, patEnc)

        assertEquals(SendAlertResult.Failed(SendAlertError.Unavailable), result)
    }

    @Test
    fun `500 es un error inesperado`() = runTest {
        api.response = errorResponse(500)

        val result = repository().sendAlert(AlertType.CRASHED, patEnc)

        assertEquals(SendAlertResult.Failed(SendAlertError.Unexpected), result)
    }

    @Test
    fun `un fallo de red es Network`() = runTest {
        api.exceptionToThrow = IOException("sin conexion")

        val result = repository().sendAlert(AlertType.CRASHED, patEnc)

        assertEquals(SendAlertResult.Failed(SendAlertError.Network), result)
    }

    @Test
    fun `circuito abierto es Unavailable, no Network`() = runTest {
        api.exceptionToThrow = CircuitBreakerOpenException("alerts")

        val result = repository().sendAlert(AlertType.CRASHED, patEnc)

        assertEquals(SendAlertResult.Failed(SendAlertError.Unavailable), result)
    }

    @Test
    fun `sin sesion guardada no llama a la red`() = runTest {
        tokenStore.clear()

        val result = repository().sendAlert(AlertType.CRASHED, patEnc)

        assertEquals(SendAlertResult.Failed(SendAlertError.Unexpected), result)
        assertEquals(0, api.callCount)
    }

    private fun errorResponse(code: Int) =
        Response.error<Unit>(code, "".toResponseBody("application/json".toMediaType()))
}
