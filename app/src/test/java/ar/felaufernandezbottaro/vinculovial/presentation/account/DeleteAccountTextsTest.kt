package ar.felaufernandezbottaro.vinculovial.presentation.account

import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteAccountTextsTest {

    private val allErrors = listOf(
        DeleteAccountError.InvalidPassword,
        DeleteAccountError.SessionExpired,
        DeleteAccountError.RateLimited,
        DeleteAccountError.Unavailable,
        DeleteAccountError.Network,
        DeleteAccountError.Unexpected,
    )

    // --- Textos del contrato, palabra por palabra ---

    @Test
    fun `la pantalla informativa dice exactamente lo que manda el contrato`() {
        assertEquals(
            listOf(
                "Se borran tu perfil, tus vehículos vinculados, tus sesiones y tus dispositivos.",
                "Las alertas que enviaste y que llegaron a su destinatario se conservan durante 3 años " +
                    "desde su envío, aunque elimines tu cuenta: el tipo, la fecha, las fotos tal como las " +
                    "vio el destinatario y, cifrados, tu DNI y la patente. Se guardan aisladas, solo se " +
                    "abren ante un pedido judicial (Ley 25.326, art. 16 inc. 5) y después se destruyen.",
                "Esta acción no se puede deshacer.",
            ),
            DeleteAccountTexts.infoParagraphs,
        )
    }

    @Test
    fun `contrasena incorrecta dice exactamente Contrasena incorrecta`() {
        assertEquals("Contraseña incorrecta", DeleteAccountError.InvalidPassword.message())
    }

    // --- Lo que nunca se le muestra al usuario ---

    @Test
    fun `ningun texto menciona reputacion, revision ni sanciones`() {
        val everyUserFacingText = DeleteAccountTexts.infoParagraphs +
            listOf(
                DeleteAccountTexts.TITLE,
                DeleteAccountTexts.PASSWORD_LABEL,
                DeleteAccountTexts.PASSWORD_REQUIRED,
                DeleteAccountTexts.CONFIRM,
                DeleteAccountTexts.RETRY,
            ) +
            allErrors.map { it.message() }

        val forbidden = listOf("reputaci", "revisi", "sanci", "penaliz", "bloque", "strike", "suspend")

        everyUserFacingText.forEach { text ->
            forbidden.forEach { word ->
                assertFalse("'$text' menciona '$word'", text.contains(word, ignoreCase = true))
            }
        }
    }

    // --- Mensajes de error ---

    @Test
    fun `429 y 503 comparten un unico error generico`() {
        assertEquals(DeleteAccountError.RateLimited.message(), DeleteAccountError.Unavailable.message())
    }

    @Test
    fun `el error generico no es el de contrasena ni el de red`() {
        val generic = DeleteAccountError.Unavailable.message()

        assertNotEquals(DeleteAccountError.InvalidPassword.message(), generic)
        assertNotEquals(DeleteAccountError.Network.message(), generic)
    }

    @Test
    fun `todos los errores tienen un mensaje no vacio`() {
        allErrors.forEach { assertTrue("$it", it.message().isNotBlank()) }
    }

    // --- Reintento ---

    @Test
    fun `429, 503, red e inesperado se reintentan tal cual`() {
        listOf(
            DeleteAccountError.RateLimited,
            DeleteAccountError.Unavailable,
            DeleteAccountError.Network,
            DeleteAccountError.Unexpected,
        ).forEach { assertTrue("$it", it.isRetryable()) }
    }

    @Test
    fun `contrasena incorrecta y sesion vencida no se reintentan tal cual`() {
        assertFalse(DeleteAccountError.InvalidPassword.isRetryable())
        assertFalse(DeleteAccountError.SessionExpired.isRetryable())
    }
}
