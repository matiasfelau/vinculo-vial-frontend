package ar.felaufernandezbottaro.vinculovial.presentation.recovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialField
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialProblem
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeFlowError
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeFlowTexts
import ar.felaufernandezbottaro.vinculovial.presentation.common.message
import ar.felaufernandezbottaro.vinculovial.presentation.signup.message
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los pasos de la recuperación de contraseña: DNI, código, contraseña nueva, el diálogo de error
 * del cierre (una acción concreta por cada causa) y la pantalla final.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class RecoveryScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    // -- Paso 1: DNI --------------------------------------------------------------------------

    private fun showDni(state: RecoveryUiState = RecoveryUiState()) {
        compose.setContent {
            VinculoVialTheme {
                RecoveryDniScreen(
                    state = state,
                    onDniChange = { calls += "dni:$it" },
                    onSubmit = { calls += "submit" },
                    onBack = { calls += "back" },
                )
            }
        }
    }

    @Test
    fun `el paso del DNI explica el recorrido y vuelve al login`() {
        showDni()

        compose.onNodeWithText("Recuperá tu contraseña").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver al inicio de sesión").performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    fun `sin DNI no se puede enviar el codigo y con DNI si`() {
        showDni()
        compose.onNodeWithText("Enviar código").assertIsNotEnabled()
    }

    @Test
    fun `con DNI enviar el codigo avisa`() {
        showDni(RecoveryUiState(dni = "12345678"))

        compose.onNodeWithText("Enviar código").assertIsEnabled().performClick()

        assertEquals(listOf("submit"), calls)
    }

    @Test
    fun `escribir el DNI avisa el cambio`() {
        showDni()

        compose.onNodeWithText("12345678").performTextInput("30111222")

        assertEquals(true, "dni:30111222" in calls)
    }

    @Test
    fun `un DNI invalido muestra su problema y un fallo del pedido tambien`() {
        showDni(RecoveryUiState(dni = "12", dniProblem = CredentialProblem.DNI_FORMAT))
        compose.onNodeWithText("El DNI debe tener 7 u 8 dígitos.").assertIsDisplayed()
    }

    @Test
    fun `un fallo del pedido de codigo se muestra en el campo`() {
        showDni(RecoveryUiState(dni = "12345678", requestError = CodeFlowError.Failure(RequestFailure.RATE_LIMITED)))

        compose.onNodeWithText(RequestFailure.RATE_LIMITED.message()).assertIsDisplayed()
    }

    @Test
    fun `mientras pide el codigo bloquea el volver`() {
        showDni(RecoveryUiState(dni = "12345678", requesting = true))

        compose.onNodeWithContentDescription("Volver al inicio de sesión").assertIsNotEnabled()
    }

    // -- Paso 2: codigo -----------------------------------------------------------------------

    private fun showCode(state: RecoveryUiState) {
        compose.setContent {
            VinculoVialTheme {
                RecoveryCodeScreen(
                    state = state,
                    onCodeChange = { calls += "code:$it" },
                    onConfirm = { calls += "confirm" },
                    onResend = { calls += "resend" },
                    onBack = { calls += "back" },
                )
            }
        }
    }

    @Test
    fun `el paso del codigo muestra el aviso anti enumeracion`() {
        showCode(RecoveryUiState(codeRequested = true))

        compose.onNodeWithText(CodeFlowTexts.RECOVERY_REQUESTED).assertIsDisplayed()
    }

    @Test
    fun `con el codigo completo se puede verificar y reenviar espera la cuenta regresiva`() {
        showCode(RecoveryUiState(codeRequested = true, code = "123456", resendSeconds = 42))

        compose.onNodeWithText("Verificar código").assertIsEnabled().performClick()
        compose.onNodeWithText(CodeFlowTexts.resendIn(42)).assertIsNotEnabled()

        assertEquals(listOf("confirm"), calls)
    }

    @Test
    fun `sin cuenta regresiva se puede reenviar y volver corrige el DNI`() {
        showCode(RecoveryUiState(codeRequested = true, code = "12"))

        compose.onNodeWithText("Verificar código").assertIsNotEnabled()
        compose.onNodeWithText(CodeFlowTexts.RESEND_AVAILABLE).assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Volver para corregir el DNI").performClick()

        assertEquals(listOf("resend", "back"), calls)
    }

    @Test
    fun `un codigo invalido muestra el mensaje del contrato`() {
        showCode(RecoveryUiState(codeRequested = true, code = "123456", codeError = CodeFlowError.InvalidCode))

        compose.onNodeWithText("El código no es válido o venció").assertIsDisplayed()
    }

    // -- Paso 3: contrasena nueva -------------------------------------------------------------

    private fun showPassword(state: RecoveryUiState) {
        compose.setContent {
            VinculoVialTheme {
                RecoveryPasswordScreen(
                    state = state,
                    onPasswordChange = { calls += "password:$it" },
                    onPasswordConfirmationChange = { calls += "confirmation:$it" },
                    onStartCapture = { calls += "capture" },
                    onErrorDismissed = { calls += "dismissed" },
                    onEditPassword = { calls += "editPassword" },
                    onRequestNewCode = { calls += "newCode" },
                    onBack = { calls += "back" },
                )
            }
        }
    }

    @Test
    fun `el paso de la contrasena pide escanear el DNI recien con las dos contrasenas`() {
        showPassword(RecoveryUiState(password = "Nueva123!", passwordConfirmation = ""))
        compose.onNodeWithText("Escanear mi DNI").assertIsNotEnabled()
    }

    @Test
    fun `con las dos contrasenas escanear el DNI arranca la captura`() {
        showPassword(RecoveryUiState(password = "Nueva123!", passwordConfirmation = "Nueva123!"))

        compose.onNodeWithText("Escanear mi DNI").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Volver").performClick()

        assertEquals(listOf("capture", "back"), calls)
    }

    @Test
    fun `el paso de la contrasena muestra el aviso de la captura`() {
        showPassword(RecoveryUiState())

        compose.onNodeWithText("Elegí tu contraseña nueva").assertIsDisplayed()
        compose.onNodeWithText(
            "Para confirmar que sos vos, ahora vamos a escanear tu DNI y a pedirte una selfie. " +
                "Las imágenes se procesan en tu dispositivo y no se guardan.",
        ).assertIsDisplayed()
    }

    @Test
    fun `los problemas de cada contrasena se muestran en su campo`() {
        showPassword(
            RecoveryUiState(
                password = "abc",
                passwordConfirmation = "xyz",
                passwordProblems = mapOf(
                    CredentialField.PASSWORD to CredentialProblem.REQUIRED,
                    CredentialField.PASSWORD_CONFIRMATION to CredentialProblem.PASSWORD_MISMATCH,
                ),
            ),
        )

        compose.onNodeWithText(CredentialProblem.REQUIRED.message()).assertIsDisplayed()
        compose.onNodeWithText(CredentialProblem.PASSWORD_MISMATCH.message()).assertIsDisplayed()
    }

    @Test
    fun `mientras valida la identidad bloquea el volver y avisa que tarda`() {
        showPassword(
            RecoveryUiState(password = "Nueva123!", passwordConfirmation = "Nueva123!", status = RecoveryStatus.Submitting),
        )

        compose.onNodeWithContentDescription("Volver").assertIsNotEnabled()
        compose.onNodeWithText("Estamos validando tu identidad. Puede tardar unos segundos.").assertIsDisplayed()
    }

    // -- Dialogo de error ---------------------------------------------------------------------

    private fun showError(error: RecoveryError) {
        showPassword(
            RecoveryUiState(
                password = "Nueva123!",
                passwordConfirmation = "Nueva123!",
                status = RecoveryStatus.Failed(error),
            ),
        )
    }

    @Test
    fun `un error de captura ofrece reintentar`() {
        showError(RecoveryError.DniMismatch)

        compose.onNodeWithText("El DNI no coincide").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf("dismissed"), calls)
    }

    @Test
    fun `un token de reset vencido ofrece pedir otro codigo`() {
        showError(RecoveryError.InvalidResetToken)

        compose.onNodeWithText("Pedir otro código").performClick()

        assertEquals(listOf("newCode"), calls)
    }

    @Test
    fun `una contrasena rechazada ofrece cambiarla`() {
        showError(RecoveryError.PasswordRejected)

        compose.onNodeWithText("Cambiar la contraseña").performClick()

        assertEquals(listOf("editPassword"), calls)
    }

    @Test
    fun `un fallo definitivo solo se puede cerrar y uno transitorio se reintenta`() {
        showError(RecoveryError.Failure(RequestFailure.DEVICE_NOT_TRUSTED))
        compose.onNodeWithText("Entendido").performClick()

        assertEquals(listOf("dismissed"), calls)
        assertEquals(
            RecoveryErrorAction.RETRY_CAPTURE,
            RecoveryError.Failure(RequestFailure.NETWORK).presentation().action,
        )
        assertEquals(
            RecoveryErrorAction.DISMISS,
            RecoveryError.Failure(RequestFailure.CONFIGURATION).presentation().action,
        )
    }

    // -- Paso final ---------------------------------------------------------------------------

    @Test
    fun `la pantalla final avisa que se cerraron las sesiones y lleva al login`() {
        compose.setContent { VinculoVialTheme { RecoveryDoneScreen(onGoToLogin = { calls += "login" }) } }

        compose.onNodeWithText("Cambiaste tu contraseña").assertIsDisplayed()
        compose.onNodeWithText("Iniciar sesión").performClick()

        assertEquals(listOf("login"), calls)
    }
}
