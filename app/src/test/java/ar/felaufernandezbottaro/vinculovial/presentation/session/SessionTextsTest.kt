package ar.felaufernandezbottaro.vinculovial.presentation.session

import ar.felaufernandezbottaro.vinculovial.domain.session.LoginNotice
import org.junit.Assert.assertEquals
import org.junit.Test

/** Los textos del contrato de US-12, palabra por palabra. */
class SessionTextsTest {

    @Test
    fun `la sesion vencida dice Tu sesion vencio`() {
        assertEquals("Tu sesión venció", LoginNotice.SESSION_EXPIRED.message())
    }

    @Test
    fun `el cambio de huellas manda a Login con la contrasena`() {
        assertEquals(
            "Cambiaron las huellas del dispositivo. Ingresá con tu contraseña",
            LoginNotice.BIOMETRICS_CHANGED.message(),
        )
    }

    @Test
    fun `la recuperacion de contrasena deja el aviso del contrato en Login`() {
        assertEquals("Listo. Ingresá con tu nueva contraseña", LoginNotice.PASSWORD_RESET.message())
    }

    @Test
    fun `las dos acciones del Desbloqueo son huella y contrasena`() {
        assertEquals("Usar huella", SessionTexts.UNLOCK_BUTTON)
        assertEquals("Usar contraseña", SessionTexts.USE_PASSWORD_BUTTON)
    }

    @Test
    fun `cada falla del desbloqueo tiene su propio mensaje`() {
        val messages = UnlockFailure.entries.map { it.message() }

        assertEquals(UnlockFailure.entries.size, messages.toSet().size)
    }
}
