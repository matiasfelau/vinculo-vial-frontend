package ar.felaufernandezbottaro.vinculovial.domain.signin.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * `SignInCredentials` carga DNI y contraseña. Un `toString()` por defecto los volcaría
 * enteros al primer log o reporte de crash que capture el objeto — por eso lo pisa. Este
 * test es la guarda contra que alguien lo borre sin darse cuenta al tocar la clase.
 */
class SignInRedactedModelsTest {

    @Test
    fun `SignInCredentials no expone ningun campo al renderizarse`() {
        val credentials = SignInCredentials(dni = "12345678", password = "Password123!")

        val rendered = credentials.toString()

        assertEquals("SignInCredentials(***)", rendered)
        listOf("12345678", "Password123!").forEach {
            assertFalse("el toString filtró '$it'", rendered.contains(it))
        }
    }
}
