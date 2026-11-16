package ar.felaufernandezbottaro.vinculovial.data.emailverification

import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException
import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.AuthWireHarness
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeConfirmResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeRequestResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.Base64

/**
 * Contrato de la verificación del email (US-13) contra el Gateway simulado: endpoint, header de
 * attestation, sobre HPKE, campos exactos del payload cifrado y mapeo de cada respuesta por `code`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmailVerificationRepositoryImplTest {

    private val wire = AuthWireHarness()

    private val email = "usuario@ejemplo.com"
    private val code = "123456"

    @Before
    fun setUp() = wire.start()

    @After
    fun tearDown() = wire.stop()

    private fun TestScope.repository() =
        with(wire) { EmailVerificationRepositoryImpl(api = api, caller = caller()) }

    // --- Pedir el código --------------------------------------------------------------------

    @Test
    fun `pedir el codigo publica en email-verifications con el header de attestation`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(202))

        val result = repository().requestCode(email)

        assertEquals(CodeRequestResult.Sent, result)
        val request = wire.server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/email-verifications", request.path)
        assertNotNull("falta el header de attestation", request.getHeader(AttestationInterceptor.HEADER))
    }

    @Test
    fun `el cuerpo del pedido es solo el sobre hpke`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(202))

        repository().requestCode(email)

        val body = wire.envelopeOf(wire.server.takeRequest())
        assertEquals(setOf("ephemeral_public_key", "ciphertext"), body.keys().asSequence().toSet())
        assertEquals(32, Base64.getDecoder().decode(body.getString("ephemeral_public_key")).size)
    }

    @Test
    fun `el payload cifrado del pedido lleva exactamente el email`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(202))

        repository().requestCode(email)

        val payload = wire.decrypt(wire.server.takeRequest())
        assertEquals(setOf("email"), payload.keys().asSequence().toSet())
        assertEquals(email, payload.getString("email"))
    }

    @Test
    fun `el email no viaja en claro`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(202))

        repository().requestCode(email)

        assertTrue(!wire.server.takeRequest().body.readUtf8().contains(email))
    }

    @Test
    fun `400 datos_invalidos al pedir el codigo es entrada invalida`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"datos_invalidos"}"""))

        assertEquals(CodeRequestResult.InvalidInput, repository().requestCode(email))
    }

    @Test
    fun `400 con otro codigo al pedir el codigo no se inventa un error especifico`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"solicitud_invalida"}"""))

        assertEquals(
            CodeRequestResult.Failed(RequestFailure.UNEXPECTED),
            repository().requestCode(email),
        )
    }

    // --- Confirmar el código -----------------------------------------------------------------

    @Test
    fun `confirmar publica en confirm y devuelve el token del 200`() = runTest {
        wire.server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"verification_token":"tok-123","expires_in":1800}"""),
        )

        val result = repository().confirmCode(email, code)

        assertEquals(CodeConfirmResult.Confirmed("tok-123"), result)
        val request = wire.server.takeRequest()
        assertEquals("/api/v1/auth/email-verifications/confirm", request.path)
        assertNotNull(request.getHeader(AttestationInterceptor.HEADER))
    }

    @Test
    fun `el payload cifrado de confirmar lleva exactamente email y code`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(200).setBody("""{"verification_token":"t"}"""))

        repository().confirmCode(email, code)

        val payload = wire.decrypt(wire.server.takeRequest())
        assertEquals(setOf("email", "code"), payload.keys().asSequence().toSet())
        assertEquals(email, payload.getString("email"))
        assertEquals(code, payload.getString("code"))
    }

    @Test
    fun `el codigo no viaja en claro`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(200).setBody("""{"verification_token":"t"}"""))

        repository().confirmCode(email, code)

        val raw = wire.server.takeRequest().body.readUtf8()
        assertTrue(!raw.contains(code))
        assertTrue(!raw.contains(email))
    }

    @Test
    fun `400 INVALID_CODE es codigo invalido`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"INVALID_CODE"}"""))

        assertEquals(CodeConfirmResult.InvalidCode, repository().confirmCode(email, code))
    }

    @Test
    fun `400 datos_invalidos al confirmar es entrada invalida`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"datos_invalidos"}"""))

        assertEquals(CodeConfirmResult.InvalidInput, repository().confirmCode(email, code))
    }

    @Test
    fun `400 sin cuerpo o con cuerpo ilegible no se confunde con codigo invalido`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(400))
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("no es json"))

        val unexpected = CodeConfirmResult.Failed(RequestFailure.UNEXPECTED)
        assertEquals(unexpected, repository().confirmCode(email, code))
        assertEquals(unexpected, repository().confirmCode(email, code))
    }

    @Test
    fun `un 200 sin token es un contrato roto, no una verificacion`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(200).setBody("""{"verification_token":""}"""))
        wire.server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val unexpected = CodeConfirmResult.Failed(RequestFailure.UNEXPECTED)
        assertEquals(unexpected, repository().confirmCode(email, code))
        assertEquals(unexpected, repository().confirmCode(email, code))
    }

    @Test
    fun `el token confirmado no se expone al renderizar el resultado`() {
        assertEquals("Confirmed(***)", CodeConfirmResult.Confirmed("tok-secreto").toString())
    }

    // --- Fallas de transporte (comunes a los dos pedidos) ---------------------------------------

    @Test
    fun `429 del rate limit es RATE_LIMITED, sin cuerpo del contrato`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(429))

        assertEquals(CodeRequestResult.Failed(RequestFailure.RATE_LIMITED), repository().requestCode(email))
    }

    @Test
    fun `503 dependencia_no_disponible es UNAVAILABLE`() = runTest {
        wire.server.enqueue(
            MockResponse().setResponseCode(503).setHeader("Retry-After", "5")
                .setBody("""{"code":"dependencia_no_disponible"}"""),
        )

        assertEquals(CodeRequestResult.Failed(RequestFailure.UNAVAILABLE), repository().requestCode(email))
    }

    @Test
    fun `502 y 504 de Kong sin cuerpo tambien son UNAVAILABLE`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(502))
        wire.server.enqueue(MockResponse().setResponseCode(504))

        assertEquals(CodeRequestResult.Failed(RequestFailure.UNAVAILABLE), repository().requestCode(email))
        assertEquals(CodeRequestResult.Failed(RequestFailure.UNAVAILABLE), repository().requestCode(email))
    }

    @Test
    fun `403 es attestation rechazada`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(403))

        assertEquals(
            CodeRequestResult.Failed(RequestFailure.ATTESTATION_REJECTED),
            repository().requestCode(email),
        )
    }

    @Test
    fun `500 es un fallo inesperado`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(CodeRequestResult.Failed(RequestFailure.UNEXPECTED), repository().requestCode(email))
    }

    @Test
    fun `sin conexion es NETWORK`() = runTest {
        wire.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(CodeRequestResult.Failed(RequestFailure.NETWORK), repository().requestCode(email))
    }

    @Test
    fun `circuito abierto es UNAVAILABLE y no sale a la red`() = runTest {
        wire.forceCircuitOpen = true

        assertEquals(CodeRequestResult.Failed(RequestFailure.UNAVAILABLE), repository().requestCode(email))
        assertEquals(0, wire.server.requestCount)
    }

    @Test
    fun `un cuerpo de exito que no se puede leer es un fallo inesperado`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(200).setBody("no es json"))

        assertEquals(
            CodeConfirmResult.Failed(RequestFailure.UNEXPECTED),
            repository().confirmCode(email, code),
        )
    }

    @Test
    fun `cada causa de attestation tiene su propio fallo`() = runTest {
        val esperados = mapOf(
            AttestationUnavailableException.Reason.DEVICE_NOT_TRUSTED to RequestFailure.DEVICE_NOT_TRUSTED,
            AttestationUnavailableException.Reason.MISCONFIGURED to RequestFailure.CONFIGURATION,
            AttestationUnavailableException.Reason.PLAY_UNAVAILABLE to RequestFailure.ATTESTATION_UNAVAILABLE,
            AttestationUnavailableException.Reason.TRANSIENT to RequestFailure.ATTESTATION_UNAVAILABLE,
        )

        esperados.forEach { (reason, failure) ->
            wire.attestationProvider = object : AttestationProvider {
                override suspend fun tokenFor(requestBody: ByteArray): String =
                    throw AttestationUnavailableException("attestation falló", reason)
            }

            assertEquals(
                "mapeo incorrecto para $reason",
                CodeRequestResult.Failed(failure),
                repository().requestCode(email),
            )
        }
        assertEquals("no debió salir a la red", 0, wire.server.requestCount)
    }

    @Test
    fun `una clave publica de Identity mal configurada es error de configuracion`() = runTest {
        val repository = with(wire) { EmailVerificationRepositoryImpl(api = api, caller = caller(publicKey = "")) }

        assertEquals(CodeRequestResult.Failed(RequestFailure.CONFIGURATION), repository.requestCode(email))
        assertEquals(0, wire.server.requestCount)
    }

    @Test
    fun `cancelar la request no se convierte en un error`() = runTest {
        // CancellationException hereda de RuntimeException: si se la captura junto al resto,
        // abandonar la pantalla termina mostrando "algo salió mal" en vez de cancelar en silencio.
        val cancelada = object : ar.felaufernandezbottaro.vinculovial.data.remote.AuthApiService {
            override suspend fun signUp(
                body: ar.felaufernandezbottaro.vinculovial.data.signup.dto.SignUpRequestDto,
            ): Response<Unit> = throw UnsupportedOperationException()

            override suspend fun signIn(
                body: ar.felaufernandezbottaro.vinculovial.data.signin.dto.SignInRequestDto,
            ): Response<ar.felaufernandezbottaro.vinculovial.data.signin.dto.SignInResponseDto> =
                throw UnsupportedOperationException()

            override suspend fun requestEmailVerification(
                body: ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.HpkeEnvelopeDto,
            ): Response<Unit> = throw CancellationException("pantalla abandonada")

            override suspend fun confirmEmailVerification(
                body: ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.HpkeEnvelopeDto,
            ): Response<ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.VerificationTokenResponseDto> =
                throw UnsupportedOperationException()

            override suspend fun requestPasswordReset(
                body: ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.HpkeEnvelopeDto,
            ): Response<Unit> = throw UnsupportedOperationException()

            override suspend fun confirmPasswordReset(
                body: ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.HpkeEnvelopeDto,
            ): Response<ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.ResetTokenResponseDto> =
                throw UnsupportedOperationException()

            override suspend fun completePasswordReset(
                body: ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.HpkeEnvelopeDto,
            ): Response<Unit> = throw UnsupportedOperationException()
        }
        val repository = with(wire) { EmailVerificationRepositoryImpl(api = cancelada, caller = caller()) }

        assertThrows(CancellationException::class.java) {
            runBlocking { repository.requestCode(email) }
        }
    }
}
