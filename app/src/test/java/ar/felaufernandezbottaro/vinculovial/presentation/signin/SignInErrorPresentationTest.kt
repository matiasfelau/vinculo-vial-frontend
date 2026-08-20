package ar.felaufernandezbottaro.vinculovial.presentation.signin

import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInError
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInErrorPresentationTest {

    private val todosLosErrores = listOf(
        SignInError.InvalidData,
        SignInError.InvalidCredentials,
        SignInError.AttestationRejected,
        SignInError.DeviceNotTrusted,
        SignInError.AttestationUnavailable,
        SignInError.Network,
        SignInError.Configuration,
        SignInError.Unexpected,
    )

    @Test
    fun `todos los errores tienen mensaje`() {
        todosLosErrores.forEach { error ->
            assertTrue("$error sin mensaje", error.message().isNotBlank())
        }
    }

    @Test
    fun `credenciales invalidas no distingue dni inexistente de contrasena incorrecta`() {
        // El contrato de US-02 exige el mismo body y el mismo timing en los dos casos
        // (anti-enumeración): el mensaje tampoco puede filtrar esa distinción.
        val texto = SignInError.InvalidCredentials.message().lowercase()

        listOf("no existe", "no está registrado", "no encontramos ese dni", "no coincide con tu cuenta")
            .forEach { termino ->
                assertTrue("el mensaje de credenciales inválidas usa '$termino'", !texto.contains(termino))
            }
    }

    @Test
    fun `credenciales invalidas menciona los dos campos por igual`() {
        val texto = SignInError.InvalidCredentials.message().lowercase()

        assertTrue("no menciona el dni", texto.contains("dni"))
        assertTrue("no menciona la contraseña", texto.contains("contraseña"))
    }

    @Test
    fun `ningun mensaje expone detalle tecnico`() {
        todosLosErrores.forEach { error ->
            val texto = error.message().lowercase()
            listOf("http", "400", "401", "403", "hpke", "jwt", "psk", "exception").forEach { termino ->
                assertTrue("$error filtró '$termino'", !texto.contains(termino))
            }
        }
    }

    @Test
    fun `ningun mensaje culpa al usuario`() {
        todosLosErrores.forEach { error ->
            val texto = error.message().lowercase()
            listOf("fraude", "falsific", "impostor", "mentir").forEach { termino ->
                assertTrue("$error usa '$termino'", !texto.contains(termino))
            }
        }
    }
}
