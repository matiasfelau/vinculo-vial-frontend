package ar.felaufernandezbottaro.vinculovial.presentation.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La pantalla de Desbloqueo: huella, "Usar contraseña" y el reintento cuando no hubo red. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class UnlockScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(status: UnlockStatus = UnlockStatus.Idle) {
        compose.setContent {
            VinculoVialTheme {
                UnlockScreen(
                    state = UnlockUiState(status),
                    onUnlock = { calls += "unlock" },
                    onUsePassword = { calls += "password" },
                )
            }
        }
    }

    @Test
    fun `muestra el titulo, el subtitulo y las dos salidas`() {
        show()

        compose.onNodeWithText(SessionTexts.UNLOCK_TITLE).assertIsDisplayed()
        compose.onNodeWithText(SessionTexts.UNLOCK_SUBTITLE).assertIsDisplayed()
        compose.onNodeWithText(SessionTexts.UNLOCK_BUTTON).performClick()
        compose.onNodeWithText(SessionTexts.USE_PASSWORD_BUTTON).performClick()

        assertEquals(listOf("unlock", "password"), calls)
    }

    @Test
    fun `sin red el boton pasa a reintentar y explica que no hace falta la contrasena`() {
        show(UnlockStatus.Failed(UnlockFailure.NETWORK))

        compose.onNodeWithText(SessionTexts.NETWORK_ERROR).assertIsDisplayed()
        compose.onNodeWithText(SessionTexts.UNLOCK_RETRY_BUTTON).assertIsEnabled()
    }

    @Test
    fun `cada falla muestra su mensaje`() {
        show(UnlockStatus.Failed(UnlockFailure.LOCKED_OUT))
        compose.onNodeWithText(SessionTexts.LOCKED_OUT_ERROR).assertIsDisplayed()
    }

    @Test
    fun `un error de la huella ofrece volver a intentar con el mismo boton`() {
        show(UnlockStatus.Failed(UnlockFailure.BIOMETRIC_ERROR))

        compose.onNodeWithText(SessionTexts.BIOMETRIC_ERROR).assertIsDisplayed()
        compose.onNodeWithText(SessionTexts.UNLOCK_BUTTON).assertIsEnabled()
    }

    @Test
    fun `mientras renueva la sesion no se puede usar la contrasena ni la huella`() {
        show(UnlockStatus.Refreshing)

        compose.onNodeWithText(SessionTexts.USE_PASSWORD_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `mientras autentica la huella la contrasena sigue disponible`() {
        show(UnlockStatus.Authenticating)

        compose.onNodeWithText(SessionTexts.USE_PASSWORD_BUTTON).assertIsEnabled()
    }
}
