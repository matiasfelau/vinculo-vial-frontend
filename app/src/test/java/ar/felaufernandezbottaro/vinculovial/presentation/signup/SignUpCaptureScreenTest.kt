package ar.felaufernandezbottaro.vinculovial.presentation.signup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ar.felaufernandezbottaro.vinculovial.domain.signup.SignUpError
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los dos pasos del onboarding biométrico (documento y selfie): qué dice cada tarjeta según su
 * estado, cuándo se puede enviar y qué se bloquea mientras algo está en curso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class SignUpCaptureScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: SignUpUiState = SignUpUiState()) {
        compose.setContent {
            VinculoVialTheme {
                SignUpCaptureScreen(
                    state = state,
                    onBack = { calls += "back" },
                    onCapture = { calls += "capture:${it.name}" },
                    onSubmit = { calls += "submit" },
                    onErrorDismissed = { calls += "dismissed" },
                    onEditForm = { calls += "editForm" },
                    onVerifyEmail = { calls += "verifyEmail" },
                )
            }
        }
    }

    @Test
    fun `sin capturas muestra las dos tarjetas con su consigna y no deja enviar`() {
        show()

        compose.onNodeWithText("Validación de identidad").assertIsDisplayed()
        compose.onNodeWithText("Tocá para escanear tu DNI").assertIsDisplayed()
        compose.onNodeWithText("Tocá y seguí la indicación en pantalla").assertIsDisplayed()
        compose.onNodeWithText("Verificar y registrarme").assertIsNotEnabled()
    }

    @Test
    fun `tocar cada tarjeta pide esa captura`() {
        show()

        compose.onNodeWithText("Documento").performClick()
        compose.onNodeWithText("Selfie").performClick()

        assertEquals(listOf("capture:DOCUMENT", "capture:SELFIE"), calls)
    }

    @Test
    fun `una captura lista se marca como lista`() {
        show(SignUpUiState(completedCaptures = setOf(CaptureTarget.DOCUMENT)))

        compose.onNodeWithText("Listo").assertIsDisplayed()
        compose.onNodeWithText("Verificar y registrarme").assertIsNotEnabled()
    }

    @Test
    fun `con las dos capturas se puede enviar`() {
        show(SignUpUiState(completedCaptures = CaptureTarget.entries.toSet()))

        compose.onNodeWithText("Verificar y registrarme").assertIsEnabled().performClick()

        assertEquals(listOf("submit"), calls)
    }

    @Test
    fun `mientras captura una tarjeta dice procesando y bloquea el resto`() {
        show(SignUpUiState(capturing = CaptureTarget.SELFIE))

        compose.onNodeWithText("Procesando…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").assertIsNotEnabled()
    }

    @Test
    fun `volver avisa`() {
        show()

        compose.onNodeWithContentDescription("Volver").performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    fun `un error del alta abre el dialogo con la accion que corresponde`() {
        show(
            SignUpUiState(
                completedCaptures = CaptureTarget.entries.toSet(),
                status = SignUpStatus.Failed(SignUpError.DniMismatch),
            ),
        )

        compose.onNodeWithText("Los datos no coinciden").assertIsDisplayed()
        compose.onNodeWithText("Revisar mis datos").performClick()

        assertEquals(listOf("editForm"), calls)
    }
}
