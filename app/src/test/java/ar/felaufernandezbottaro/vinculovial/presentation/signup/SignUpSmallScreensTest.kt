package ar.felaufernandezbottaro.vinculovial.presentation.signup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure
import ar.felaufernandezbottaro.vinculovial.domain.signup.SignUpError
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeFlowError
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeFlowTexts
import ar.felaufernandezbottaro.vinculovial.presentation.common.message
import ar.felaufernandezbottaro.vinculovial.presentation.splash.SplashScreen
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las pantallas chicas del alta: verificación de email, consentimiento, éxito, el diálogo de error
 * (un botón distinto según qué puede hacer el usuario) y el Splash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class SignUpSmallScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    // -- Verificacion de email ----------------------------------------------------------------

    private fun showVerify(state: VerifyEmailUiState) {
        compose.setContent {
            VinculoVialTheme {
                VerifyEmailScreen(
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
    fun `la verificacion nombra el email al que se mando el codigo`() {
        showVerify(VerifyEmailUiState(email = "u@ejemplo.com", codeRequested = true))

        compose.onNodeWithText("Verificá tu email").assertIsDisplayed()
        compose.onNodeWithText("Te enviamos un código de 6 dígitos a u@ejemplo.com", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `con el codigo completo se confirma y volver corrige el email`() {
        showVerify(VerifyEmailUiState(email = "u@ejemplo.com", codeRequested = true, code = "123456"))

        compose.onNodeWithText("Verificar código").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Volver al formulario para corregir el email").performClick()

        assertEquals(listOf("confirm", "back"), calls)
    }

    @Test
    fun `mientras corre la cuenta regresiva no se puede reenviar`() {
        showVerify(VerifyEmailUiState(email = "u@ejemplo.com", codeRequested = true, resendSeconds = 30))

        compose.onNodeWithText(CodeFlowTexts.resendIn(30)).assertIsNotEnabled()
    }

    @Test
    fun `terminada la cuenta regresiva se puede reenviar`() {
        showVerify(VerifyEmailUiState(email = "u@ejemplo.com", codeRequested = true, resendSeconds = 0))

        compose.onNodeWithText(CodeFlowTexts.RESEND_AVAILABLE).assertIsEnabled().performClick()

        assertEquals(listOf("resend"), calls)
    }

    @Test
    fun `un codigo invalido o un fallo de red se muestran`() {
        showVerify(
            VerifyEmailUiState(
                email = "u@ejemplo.com",
                codeRequested = true,
                code = "123456",
                error = CodeFlowError.Failure(RequestFailure.NETWORK),
            ),
        )

        compose.onNodeWithText(RequestFailure.NETWORK.message()).assertIsDisplayed()
    }

    @Test
    fun `mientras confirma bloquea el volver`() {
        showVerify(VerifyEmailUiState(email = "u@ejemplo.com", codeRequested = true, code = "123456", confirming = true))

        compose.onNodeWithContentDescription("Volver al formulario para corregir el email").assertIsNotEnabled()
    }

    // -- Consentimiento y exito ---------------------------------------------------------------

    @Test
    fun `el consentimiento explica que se hace con los datos y arranca la validacion`() {
        compose.setContent {
            VinculoVialTheme {
                SignUpConsentScreen(onBack = { calls += "back" }, onStartValidation = { calls += "start" })
            }
        }

        compose.onNodeWithText("Renaper", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Iniciar Validación").performClick()
        compose.onNodeWithContentDescription("Validación con cámara").performClick()
        compose.onNodeWithContentDescription("Volver al formulario").performClick()

        assertEquals(listOf("start", "start", "back"), calls)
    }

    @Test
    fun `la pantalla de exito confirma la cuenta y finaliza`() {
        compose.setContent { VinculoVialTheme { SignUpSuccessScreen(onFinish = { calls += "finish" }) } }

        compose.onNodeWithText("¡Cuenta creada!").assertIsDisplayed()
        compose.onNodeWithText("Finalizar").performClick()

        assertEquals(listOf("finish"), calls)
    }

    // -- Dialogo de error del alta ------------------------------------------------------------

    private fun showError(error: SignUpError) {
        compose.setContent {
            VinculoVialTheme {
                SignUpErrorDialog(
                    error = error,
                    onDismiss = { calls += "dismiss" },
                    onEditForm = { calls += "editForm" },
                    onVerifyEmail = { calls += "verifyEmail" },
                )
            }
        }
    }

    @Test
    fun `un error de captura se cierra con reintentar`() {
        showError(SignUpError.DocumentUnreadable)

        compose.onNodeWithText("No pudimos leer tu DNI").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `un error de datos ofrece revisar el formulario o repetir la captura`() {
        showError(SignUpError.DniMismatch)

        compose.onNodeWithText("Revisar mis datos").performClick()
        compose.onNodeWithText("Repetir captura").performClick()

        assertEquals(listOf("editForm", "dismiss"), calls)
    }

    @Test
    fun `un email sin verificar ofrece volver a verificarlo`() {
        showError(SignUpError.EmailNotVerified)

        compose.onNodeWithText("Verificar mi email").performClick()

        assertEquals(listOf("verifyEmail"), calls)
    }

    @Test
    fun `un error sin arreglo solo se puede entender`() {
        showError(SignUpError.DeviceNotTrusted)

        compose.onNodeWithText("Entendido").performClick()

        assertEquals(listOf("dismiss"), calls)
    }

    // -- Splash -------------------------------------------------------------------------------

    @Test
    fun `el splash avisa cuando termina`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { VinculoVialTheme { SplashScreen(onFinished = { calls += "finished" }) } }

        compose.mainClock.advanceTimeBy(60_000)

        assertEquals(listOf("finished"), calls)
    }
}
