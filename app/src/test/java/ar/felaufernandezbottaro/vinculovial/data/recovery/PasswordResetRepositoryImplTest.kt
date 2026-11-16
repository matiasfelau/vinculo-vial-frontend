package ar.felaufernandezbottaro.vinculovial.data.recovery

import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.AuthWireHarness
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeConfirmResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeRequestResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure
import ar.felaufernandezbottaro.vinculovial.domain.recovery.PasswordResetCompletion
import ar.felaufernandezbottaro.vinculovial.domain.recovery.PasswordResetResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contrato de la recuperación de contraseña (US-13) contra el Gateway simulado: los tres
 * endpoints, el header de attestation, el sobre HPKE, los campos exactos de cada payload cifrado y
 * el mapeo de cada respuesta por `code`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PasswordResetRepositoryImplTest {

    private val wire = AuthWireHarness()

    private val dni = "12345678"
    private val code = "654321"

    private val completion = PasswordResetCompletion(
        resetToken = "reset-token-123",
        dni = "12345678",
        procedureNumber = "00307513148",
        sex = "F",
        selfieBase64 = "c2VsZmll",
        newPassword = "Nueva-Clave-2026!",
    )

    @Before
    fun setUp() = wire.start()

    @After
    fun tearDown() = wire.stop()

    private fun TestScope.repository() =
        with(wire) { PasswordResetRepositoryImpl(api = api, caller = caller()) }

    // --- Pedir el código --------------------------------------------------------------------

    @Test
    fun `pedir el codigo publica en password-resets y el payload lleva solo el dni`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(202))

        val result = repository().requestCode(dni)

        assertEquals(CodeRequestResult.Sent, result)
        val request = wire.server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/password-resets", request.path)
        assertNotNull("falta el header de attestation", request.getHeader(AttestationInterceptor.HEADER))
        val payload = wire.decrypt(request)
        assertEquals(setOf("dni"), payload.keys().asSequence().toSet())
        assertEquals(dni, payload.getString("dni"))
    }

    @Test
    fun `el dni no viaja en claro`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(202))

        repository().requestCode(dni)

        assertFalse(wire.server.takeRequest().body.readUtf8().contains(dni))
    }

    @Test
    fun `400 datos_invalidos al pedir el codigo es entrada invalida`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"datos_invalidos"}"""))

        assertEquals(CodeRequestResult.InvalidInput, repository().requestCode(dni))
    }

    @Test
    fun `400 solicitud_invalida al pedir el codigo es un fallo inesperado`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"solicitud_invalida"}"""))

        assertEquals(CodeRequestResult.Failed(RequestFailure.UNEXPECTED), repository().requestCode(dni))
    }

    @Test
    fun `429 al pedir el codigo es RATE_LIMITED`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(429))

        assertEquals(CodeRequestResult.Failed(RequestFailure.RATE_LIMITED), repository().requestCode(dni))
    }

    // --- Confirmar el código -----------------------------------------------------------------

    @Test
    fun `confirmar publica en confirm y devuelve el reset_token`() = runTest {
        wire.server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"reset_token":"rt-abc","expires_in":900}"""),
        )

        val result = repository().confirmCode(dni, code)

        assertEquals(CodeConfirmResult.Confirmed("rt-abc"), result)
        val request = wire.server.takeRequest()
        assertEquals("/api/v1/auth/password-resets/confirm", request.path)
        val payload = wire.decrypt(request)
        assertEquals(setOf("dni", "code"), payload.keys().asSequence().toSet())
        assertEquals(dni, payload.getString("dni"))
        assertEquals(code, payload.getString("code"))
    }

    @Test
    fun `400 INVALID_CODE es codigo invalido`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"INVALID_CODE"}"""))

        assertEquals(CodeConfirmResult.InvalidCode, repository().confirmCode(dni, code))
    }

    @Test
    fun `400 datos_invalidos al confirmar es entrada invalida`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"datos_invalidos"}"""))

        assertEquals(CodeConfirmResult.InvalidInput, repository().confirmCode(dni, code))
    }

    @Test
    fun `un 400 con otro codigo al confirmar no se confunde con codigo invalido`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"INVALID_RESET_TOKEN"}"""))

        assertEquals(CodeConfirmResult.Failed(RequestFailure.UNEXPECTED), repository().confirmCode(dni, code))
    }

    @Test
    fun `un 200 sin reset_token es un contrato roto`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        assertEquals(CodeConfirmResult.Failed(RequestFailure.UNEXPECTED), repository().confirmCode(dni, code))
    }

    // --- Completar ---------------------------------------------------------------------------

    @Test
    fun `204 cambia la contrasena y publica en complete con el header de attestation`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(204))

        val result = repository().complete(completion)

        assertEquals(PasswordResetResult.Done, result)
        val request = wire.server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/password-resets/complete", request.path)
        assertNotNull(request.getHeader(AttestationInterceptor.HEADER))
    }

    @Test
    fun `el payload cifrado de complete lleva exactamente los campos del contrato`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(204))

        repository().complete(completion)

        val payload = wire.decrypt(wire.server.takeRequest())
        assertEquals(
            setOf("reset_token", "dni", "tramite", "sexo", "selfie", "new_password"),
            payload.keys().asSequence().toSet(),
        )
        assertEquals(completion.resetToken, payload.getString("reset_token"))
        assertEquals(completion.dni, payload.getString("dni"))
        assertEquals(completion.procedureNumber, payload.getString("tramite"))
        assertEquals(completion.sex, payload.getString("sexo"))
        assertEquals(completion.selfieBase64, payload.getString("selfie"))
        assertEquals(completion.newPassword, payload.getString("new_password"))
    }

    @Test
    fun `complete no deja nada en claro en el cuerpo`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(204))

        repository().complete(completion)

        val raw = wire.server.takeRequest().body.readUtf8()
        listOf(completion.resetToken, completion.dni, completion.newPassword, completion.selfieBase64).forEach {
            assertFalse("el cuerpo filtró '$it'", raw.contains(it))
        }
    }

    @Test
    fun `400 INVALID_RESET_TOKEN pide volver a pedir el codigo`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"INVALID_RESET_TOKEN"}"""))

        assertEquals(PasswordResetResult.InvalidResetToken, repository().complete(completion))
    }

    @Test
    fun `400 identidad_no_verificada es identidad no verificada`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"identidad_no_verificada"}"""))

        assertEquals(PasswordResetResult.IdentityNotVerified, repository().complete(completion))
    }

    @Test
    fun `datos_invalidos con password_debil o password_predecible es contrasena rechazada`() = runTest {
        listOf("password_debil", "password_predecible").forEach { detailCode ->
            wire.server.enqueue(
                MockResponse().setResponseCode(400).setBody(
                    """{"code":"datos_invalidos","details":[{"field":"new_password","code":"$detailCode"}]}""",
                ),
            )

            assertEquals(
                "mapeo incorrecto para $detailCode",
                PasswordResetResult.PasswordRejected,
                repository().complete(completion),
            )
        }
    }

    @Test
    fun `un campo de captura rechazado gana sobre la contrasena`() = runTest {
        // Arreglar la contraseña no sirve si la captura igual se va a rechazar.
        wire.server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"code":"datos_invalidos","details":[""" +
                    """{"field":"new_password","code":"password_debil"},""" +
                    """{"field":"selfie","code":"formato_invalido"}]}""",
            ),
        )

        assertEquals(PasswordResetResult.InvalidData, repository().complete(completion))
    }

    @Test
    fun `datos_invalidos sin detalle de contrasena es dato invalido generico`() = runTest {
        wire.server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"code":"datos_invalidos","details":[{"field":"tramite","code":"requerido"}]}""",
            ),
        )
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"datos_invalidos"}"""))

        assertEquals(PasswordResetResult.InvalidData, repository().complete(completion))
        assertEquals(PasswordResetResult.InvalidData, repository().complete(completion))
    }

    @Test
    fun `un 400 sin cuerpo o con un codigo desconocido es dato invalido, sin excepcion`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(400))
        wire.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"algo_nuevo"}"""))

        assertEquals(PasswordResetResult.InvalidData, repository().complete(completion))
        assertEquals(PasswordResetResult.InvalidData, repository().complete(completion))
    }

    @Test
    fun `503 dependencia_no_disponible no se confunde con un rechazo de identidad`() = runTest {
        wire.server.enqueue(
            MockResponse().setResponseCode(503).setHeader("Retry-After", "5")
                .setBody("""{"code":"dependencia_no_disponible"}"""),
        )

        assertEquals(
            PasswordResetResult.Failed(RequestFailure.UNAVAILABLE),
            repository().complete(completion),
        )
    }

    @Test
    fun `429 y 403 al completar son fallos de transporte`() = runTest {
        wire.server.enqueue(MockResponse().setResponseCode(429))
        wire.server.enqueue(MockResponse().setResponseCode(403))

        assertEquals(PasswordResetResult.Failed(RequestFailure.RATE_LIMITED), repository().complete(completion))
        assertEquals(
            PasswordResetResult.Failed(RequestFailure.ATTESTATION_REJECTED),
            repository().complete(completion),
        )
    }

    @Test
    fun `circuito abierto al completar no sale a la red`() = runTest {
        wire.forceCircuitOpen = true

        assertEquals(PasswordResetResult.Failed(RequestFailure.UNAVAILABLE), repository().complete(completion))
        assertEquals(0, wire.server.requestCount)
    }

    @Test
    fun `la solicitud de completar nunca expone sus datos al renderizarse`() {
        val rendered = completion.toString()

        assertEquals("PasswordResetCompletion(***)", rendered)
        listOf(completion.resetToken, completion.dni, completion.newPassword, completion.selfieBase64).forEach {
            assertTrue("el toString filtró '$it'", !rendered.contains(it))
        }
    }
}
