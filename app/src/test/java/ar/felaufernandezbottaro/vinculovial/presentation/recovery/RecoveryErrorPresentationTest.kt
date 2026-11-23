package ar.felaufernandezbottaro.vinculovial.presentation.recovery

import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeFlowError
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeFlowTexts
import ar.felaufernandezbottaro.vinculovial.presentation.common.message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryErrorPresentationTest {

    private val todosLosErrores: List<RecoveryError> = listOf(
        RecoveryError.DniMismatch,
        RecoveryError.LivenessFailed,
        RecoveryError.CaptureUnusable,
        RecoveryError.IdentityNotVerified,
        RecoveryError.InvalidResetToken,
        RecoveryError.PasswordRejected,
        RecoveryError.InvalidData,
    ) + RequestFailure.entries.map { RecoveryError.Failure(it) }

    @Test
    fun `todos los errores tienen titulo y mensaje`() {
        todosLosErrores.forEach { error ->
            val presentation = error.presentation()
            assertTrue("$error sin título", presentation.title.isNotBlank())
            assertTrue("$error sin mensaje", presentation.message.isNotBlank())
        }
    }

    @Test
    fun `la identidad no validada usa el texto del contrato`() {
        assertEquals(
            "No pudimos validar tu identidad",
            RecoveryError.IdentityNotVerified.presentation().title,
        )
    }

    @Test
    fun `los mensajes de identidad no distinguen no es la misma persona de no se pudo verificar`() {
        listOf(RecoveryError.IdentityNotVerified, RecoveryError.LivenessFailed).forEach { error ->
            val texto = error.presentation().let { it.title + it.message }.lowercase()
            listOf("no sos vos", "otra persona", "falso", "fraude", "suplant").forEach {
                assertTrue("$error acusa con '$it'", !texto.contains(it))
            }
        }
    }

    @Test
    fun `cada error lleva a la accion que el usuario puede hacer`() {
        assertEquals(RecoveryErrorAction.REQUEST_NEW_CODE, RecoveryError.InvalidResetToken.presentation().action)
        assertEquals(RecoveryErrorAction.EDIT_PASSWORD, RecoveryError.PasswordRejected.presentation().action)
        assertEquals(RecoveryErrorAction.EDIT_PASSWORD, RecoveryError.InvalidData.presentation().action)
        listOf(
            RecoveryError.DniMismatch,
            RecoveryError.LivenessFailed,
            RecoveryError.CaptureUnusable,
            RecoveryError.IdentityNotVerified,
        ).forEach { assertEquals("$it", RecoveryErrorAction.RETRY_CAPTURE, it.presentation().action) }
    }

    @Test
    fun `solo las fallas transitorias invitan a reintentar`() {
        val reintentables = setOf(
            RequestFailure.NETWORK,
            RequestFailure.UNAVAILABLE,
            RequestFailure.RATE_LIMITED,
            RequestFailure.ATTESTATION_UNAVAILABLE,
            RequestFailure.UNEXPECTED,
        )

        RequestFailure.entries.forEach { failure ->
            val action = RecoveryError.Failure(failure).presentation().action
            val esperada = if (failure in reintentables) RecoveryErrorAction.RETRY_CAPTURE else RecoveryErrorAction.DISMISS
            assertEquals("$failure", esperada, action)
        }
    }

    @Test
    fun `cada falla de transporte tiene su propio mensaje`() {
        val mensajes = RequestFailure.entries.map { it.message() }

        assertEquals(RequestFailure.entries.size, mensajes.toSet().size)
        mensajes.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `los textos del contrato de la recuperacion son literales`() {
        assertEquals("El código no es válido o venció", CodeFlowError.InvalidCode.message())
        assertEquals(
            "Si el DNI corresponde a una cuenta, te enviamos un código al email registrado",
            CodeFlowTexts.RECOVERY_REQUESTED,
        )
    }

    @Test
    fun `la cuenta regresiva del reenvio muestra los segundos`() {
        assertEquals("Reenviar código en 42s", CodeFlowTexts.resendIn(42))
    }

    @Test
    fun `los errores de formato y de transporte de un codigo tienen mensaje`() {
        assertEquals(CodeFlowTexts.INVALID_INPUT, CodeFlowError.InvalidInput.message())
        assertEquals(
            RequestFailure.NETWORK.message(),
            CodeFlowError.Failure(RequestFailure.NETWORK).message(),
        )
    }
}
