package ar.felaufernandezbottaro.vinculovial.domain.signin

import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInForm
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInUseCaseTest {

    private val repository = FakeSignInRepository()
    private val useCase = SignInUseCase(repository)

    private val form = SignInForm(dni = "12345678", password = "Password123!")

    @Test
    fun `login exitoso delega en el repositorio con las credenciales del formulario`() = runTest {
        val result = useCase(form)

        assertEquals(SignInResult.SignedIn, result)
        with(repository.received.single()) {
            assertEquals("12345678", dni)
            assertEquals("Password123!", password)
        }
    }

    @Test
    fun `formulario invalido no llega al repositorio`() = runTest {
        val result = useCase(form.copy(dni = "no-es-un-dni"))

        assertEquals(SignInResult.Failed(SignInError.InvalidData), result)
        assertTrue(repository.received.isEmpty())
    }

    @Test
    fun `contrasena vacia no llega al repositorio`() = runTest {
        val result = useCase(form.copy(password = ""))

        assertEquals(SignInResult.Failed(SignInError.InvalidData), result)
        assertTrue(repository.received.isEmpty())
    }

    @Test
    fun `el dni viaja sin espacios sobrantes`() = runTest {
        useCase(form.copy(dni = "  12345678  "))

        assertEquals("12345678", repository.received.single().dni)
    }

    @Test
    fun `el error del backend se propaga tal cual`() = runTest {
        repository.result = SignInResult.Failed(SignInError.InvalidCredentials)

        assertEquals(SignInResult.Failed(SignInError.InvalidCredentials), useCase(form))
    }
}
