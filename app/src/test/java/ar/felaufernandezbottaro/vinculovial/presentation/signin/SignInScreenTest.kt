package ar.felaufernandezbottaro.vinculovial.presentation.signin

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInError
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInField
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInFieldProblem
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInForm
import ar.felaufernandezbottaro.vinculovial.domain.session.LoginNotice
import ar.felaufernandezbottaro.vinculovial.presentation.session.message
import ar.felaufernandezbottaro.vinculovial.presentation.session.SessionTexts
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El Login: qué muestra según el estado (aviso de por qué se llegó, errores por campo, error
 * genérico anti-enumeración, enviando) y qué callbacks dispara cada control.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class SignInScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: SignInUiState = SignInUiState(), notice: LoginNotice? = null) {
        compose.setContent {
            VinculoVialTheme {
                SignInScreen(
                    state = state,
                    notice = notice,
                    onDniChange = { calls += "dni:$it" },
                    onPasswordChange = { calls += "password:$it" },
                    onSubmit = { calls += "submit" },
                    onGoToSignUp = { calls += "signup" },
                    onForgotPassword = { calls += "forgot" },
                )
            }
        }
    }

    @Test
    fun `muestra el titulo y los dos campos`() {
        show()

        compose.onAllNodesWithText("Iniciar sesión").assertCountEquals(2) // titular y botón
        compose.onNodeWithText("DNI").assertIsDisplayed()
        compose.onNodeWithText("CONTRASEÑA").assertIsDisplayed()
    }

    @Test
    fun `escribir en los campos avisa cada cambio`() {
        show()

        compose.onNodeWithText("12345678").performTextInput("30111222")
        compose.onNodeWithText("••••••••").performTextInput("Secreta123!")

        // El campo es controlado (la pantalla no guarda el texto), así que alcanza con que cada
        // valor escrito haya llegado al callback de su campo.
        assertTrue("dni:30111222" in calls)
        assertTrue("password:Secreta123!" in calls)
    }

    @Test
    fun `el boton y los enlaces disparan sus callbacks`() {
        show()

        compose.onNodeWithText("Registrate aquí", substring = true).performClick()
        compose.onNodeWithText("¿Olvidaste tu contraseña?").performClick()
        compose.onAllNodesWithText("Iniciar sesión")[1].performClick()

        assertEquals(listOf("signup", "forgot", "submit"), calls)
    }

    @Test
    fun `un aviso explica por que se llego al login`() {
        show(notice = LoginNotice.SESSION_EXPIRED)

        compose.onNodeWithText(SessionTexts.SESSION_EXPIRED).assertIsDisplayed()
    }

    @Test
    fun `cada aviso tiene su texto`() {
        assertEquals(SessionTexts.SESSION_EXPIRED, LoginNotice.SESSION_EXPIRED.message())
        assertEquals(SessionTexts.BIOMETRICS_CHANGED, LoginNotice.BIOMETRICS_CHANGED.message())
        assertEquals(SessionTexts.PASSWORD_RESET, LoginNotice.PASSWORD_RESET.message())
    }

    @Test
    fun `los problemas por campo se muestran junto a su campo`() {
        show(
            SignInUiState(
                form = SignInForm(dni = "12", password = ""),
                fieldErrors = mapOf(
                    SignInField.DNI to SignInFieldProblem.DNI_FORMAT,
                    SignInField.PASSWORD to SignInFieldProblem.REQUIRED,
                ),
            ),
        )

        compose.onNodeWithText("El DNI debe tener 7 u 8 dígitos.").assertIsDisplayed()
        compose.onNodeWithText("Completá este campo.").assertIsDisplayed()
    }

    @Test
    fun `una falla muestra el mensaje generico de credenciales`() {
        show(SignInUiState(status = SignInStatus.Failed(SignInError.InvalidCredentials)))

        compose.onNodeWithText("DNI o contraseña incorrectos.").assertIsDisplayed()
    }

    @Test
    fun `cada error de login tiene un mensaje propio`() {
        val errors = listOf(
            SignInError.InvalidData,
            SignInError.InvalidCredentials,
            SignInError.AttestationRejected,
            SignInError.DeviceNotTrusted,
            SignInError.AttestationUnavailable,
            SignInError.Unavailable,
            SignInError.Network,
            SignInError.Configuration,
            SignInError.Unexpected,
        )

        val messages = errors.map { it.message() }

        assertEquals(errors.size, messages.toSet().size)
        assertEquals("Revisá el DNI y la contraseña ingresados.", SignInError.InvalidData.message())
    }

    @Test
    fun `mientras envia bloquea los controles`() {
        show(SignInUiState(status = SignInStatus.Submitting))

        compose.onNodeWithText("¿Olvidaste tu contraseña?").assertIsNotEnabled()
    }

    @Test
    fun `sin enviar el olvido de contrasena esta habilitado`() {
        show()

        compose.onNodeWithText("¿Olvidaste tu contraseña?").assertIsEnabled()
    }
}
