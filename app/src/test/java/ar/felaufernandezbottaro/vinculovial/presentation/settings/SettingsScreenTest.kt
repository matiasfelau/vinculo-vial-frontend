package ar.felaufernandezbottaro.vinculovial.presentation.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ar.felaufernandezbottaro.vinculovial.presentation.session.SessionTexts
import ar.felaufernandezbottaro.vinculovial.presentation.terms.TermsTexts
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El Home: las acciones principales, el interruptor de la huella, los diálogos (oferta de huella,
 * fallo de activación y confirmación de cierre de sesión) y qué se bloquea al cerrar sesión.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: SettingsUiState = SettingsUiState()) {
        compose.setContent {
            VinculoVialTheme {
                SettingsScreen(
                    state = state,
                    onLogout = { calls += "logout" },
                    onOpenVehicles = { calls += "vehicles" },
                    onScanPlate = { calls += "scan" },
                    onOpenInbox = { calls += "inbox" },
                    onDeleteAccount = { calls += "delete" },
                    onBiometricToggle = { calls += "biometric:$it" },
                    onBiometricOfferDismissed = { calls += "offerDismissed" },
                    onBiometricActivationFailureDismissed = { calls += "failureDismissed" },
                    onOpenTerms = { calls += "terms" },
                )
            }
        }
    }

    @Test
    fun `las tarjetas principales abren su flujo`() {
        show()

        compose.onNodeWithText("Mis vehículos").performClick()
        compose.onNodeWithText("Escanear patente").performClick()
        compose.onNodeWithText("Mis alertas").performClick()

        assertEquals(listOf("vehicles", "scan", "inbox"), calls)
    }

    @Test
    fun `los accesos de pie abren Terminos y la baja de cuenta`() {
        show()

        compose.onNodeWithText(TermsTexts.SCREEN_TITLE).performScrollTo().performClick()
        compose.onNodeWithText("Eliminar cuenta").performScrollTo().performClick()

        assertEquals(listOf("terms", "delete"), calls)
    }

    @Test
    fun `sin biometria disponible no hay interruptor de huella`() {
        show(SettingsUiState(isBiometricAvailable = false))

        compose.onNodeWithText(SessionTexts.SETTING_TITLE).assertDoesNotExist()
    }

    @Test
    fun `con biometria disponible muestra el interruptor`() {
        show(SettingsUiState(isBiometricAvailable = true))

        compose.onNodeWithText(SessionTexts.SETTING_TITLE).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(SessionTexts.SETTING_SUBTITLE).assertIsDisplayed()
    }

    @Test
    fun `la oferta de huella se acepta o se descarta`() {
        show(SettingsUiState(showBiometricOffer = true))

        compose.onNodeWithText(SessionTexts.OFFER_TITLE).assertIsDisplayed()
        compose.onNodeWithText(SessionTexts.OFFER_ACCEPT).performClick()
        compose.onNodeWithText(SessionTexts.OFFER_DISMISS).performClick()

        assertEquals(listOf("biometric:true", "offerDismissed"), calls)
    }

    @Test
    fun `si la huella no se pudo activar lo avisa y se cierra`() {
        show(SettingsUiState(biometricActivationFailed = true))

        compose.onNodeWithText(SessionTexts.ACTIVATION_FAILED).assertIsDisplayed()
        compose.onNodeWithText(SessionTexts.ACTIVATION_FAILED_DISMISS).performClick()

        assertEquals(listOf("failureDismissed"), calls)
    }

    @Test
    fun `cerrar sesion pide confirmacion antes de avisar`() {
        show()

        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
        assertEquals(emptyList<String>(), calls)
        compose.onNodeWithText("¿Cerrar sesión?").assertIsDisplayed()
        compose.onNodeWithText("Sí, cerrar sesión").performClick()

        assertEquals(listOf("logout"), calls)
    }

    @Test
    fun `seguir en la confirmacion no cierra la sesion`() {
        show()

        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Seguir").performClick()

        assertEquals(emptyList<String>(), calls)
        compose.onNodeWithText("¿Cerrar sesión?").assertDoesNotExist()
    }

    @Test
    fun `mientras cierra la sesion se bloquean las acciones`() {
        show(SettingsUiState(isLoggingOut = true, isBiometricAvailable = true))

        compose.onNodeWithText("Mis vehículos").assertIsNotEnabled()
        compose.onNodeWithText("Escanear patente").assertIsNotEnabled()
        compose.onNodeWithText("Mis alertas").assertIsNotEnabled()
        compose.onNodeWithText("Eliminar cuenta").performScrollTo().assertIsNotEnabled()
    }
}
