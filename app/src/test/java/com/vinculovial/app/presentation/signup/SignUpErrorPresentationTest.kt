package com.vinculovial.app.presentation.signup

import com.vinculovial.app.domain.signup.SignUpError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpErrorPresentationTest {

    private val todosLosErrores = listOf(
        SignUpError.DocumentUnreadable,
        SignUpError.DocumentNotRecognized,
        SignUpError.LivenessFailed,
        SignUpError.DniMismatch,
        SignUpError.InvalidData,
        SignUpError.AttestationRejected,
        SignUpError.DeviceNotTrusted,
        SignUpError.AttestationUnavailable,
        SignUpError.AlreadyRegistered,
        SignUpError.Network,
        SignUpError.Configuration,
        SignUpError.Unexpected,
    )

    @Test
    fun `todos los errores tienen titulo y mensaje`() {
        todosLosErrores.forEach { error ->
            val presentation = error.presentation()
            assertTrue("$error sin título", presentation.title.isNotBlank())
            assertTrue("$error sin mensaje", presentation.message.isNotBlank())
        }
    }

    @Test
    fun `el conflicto no revela que dato ya existe`() {
        val presentation = SignUpError.AlreadyRegistered.presentation()

        val texto = (presentation.title + presentation.message).lowercase()
        listOf("dni", "documento", "email", "correo").forEach {
            assertTrue("el mensaje de 409 menciona '$it'", !texto.contains(it))
        }
    }

    @Test
    fun `los errores de verificacion invitan a repetir la captura`() {
        listOf(
            SignUpError.DocumentUnreadable,
            SignUpError.DocumentNotRecognized,
            SignUpError.LivenessFailed,
        ).forEach { error ->
            assertEquals(
                "$error debería invitar a recapturar",
                SignUpErrorAction.RETRY_CAPTURE,
                error.presentation().action,
            )
        }
    }

    @Test
    fun `ningun mensaje culpa al usuario de fraude`() {
        // Un falso positivo de la verificación es esperable (ahora la corre RENAPER del
        // lado del backend, pero el principio es el mismo): ningún mensaje puede acusar.
        todosLosErrores.forEach { error ->
            val texto = error.presentation().message.lowercase()
            listOf("fraude", "falsific", "impostor", "mentir").forEach { termino ->
                assertTrue("$error usa '$termino'", !texto.contains(termino))
            }
        }
    }

    @Test
    fun `los errores de datos mandan al formulario`() {
        assertEquals(SignUpErrorAction.EDIT_FORM, SignUpError.DniMismatch.presentation().action)
        assertEquals(SignUpErrorAction.EDIT_FORM, SignUpError.InvalidData.presentation().action)
        assertEquals(SignUpErrorAction.EDIT_FORM, SignUpError.AlreadyRegistered.presentation().action)
    }

    @Test
    fun `los errores sin accion del usuario solo se cierran`() {
        assertEquals(SignUpErrorAction.DISMISS, SignUpError.AttestationRejected.presentation().action)
        assertEquals(SignUpErrorAction.DISMISS, SignUpError.Configuration.presentation().action)
    }

    @Test
    fun `ningun mensaje expone detalle tecnico`() {
        todosLosErrores.forEach { error ->
            val texto = error.presentation().message.lowercase()
            listOf("http", "403", "409", "hpke", "jwt", "psk", "exception").forEach { termino ->
                assertTrue("$error filtró '$termino'", !texto.contains(termino))
            }
        }
    }
}
