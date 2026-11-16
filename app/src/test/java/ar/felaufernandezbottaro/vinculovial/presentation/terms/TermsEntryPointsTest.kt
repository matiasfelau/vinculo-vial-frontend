package ar.felaufernandezbottaro.vinculovial.presentation.terms

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.AlertDetailUiState
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.FEEDBACK_TERMS_TAG
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.InboxDetailScreen
import ar.felaufernandezbottaro.vinculovial.presentation.settings.SettingsScreen
import ar.felaufernandezbottaro.vinculovial.presentation.settings.SettingsUiState
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.welcome.WelcomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Los tres lugares, además del alta, desde donde se llega a los Términos (US-19): Bienvenida, Settings y la valoración. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class TermsEntryPointsTest {

    @get:Rule
    val compose = createComposeRule()

    private var termsOpened = 0

    // -- Settings -----------------------------------------------------------------------------

    private fun showSettings() {
        compose.setContent {
            VinculoVialTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    onLogout = {},
                    onLinkVehicle = {},
                    onScanPlate = {},
                    onOpenInbox = {},
                    onDeleteAccount = {},
                    onBiometricToggle = {},
                    onBiometricOfferDismissed = {},
                    onBiometricActivationFailureDismissed = {},
                    onOpenTerms = { termsOpened++ },
                )
            }
        }
    }

    @Test
    fun `Settings tiene el acceso a Terminos y Condiciones`() {
        showSettings()

        compose.onNodeWithText("Términos y Condiciones").assertIsDisplayed()
    }

    @Test
    fun `desde Settings, Terminos y Condiciones abre la pantalla de Terminos`() {
        showSettings()

        compose.onNodeWithText("Términos y Condiciones").performClick()

        assertEquals(1, termsOpened)
    }

    // -- Bienvenida ---------------------------------------------------------------------------

    @Test
    fun `la Bienvenida lleva a los Terminos sin tener cuenta ni sesion`() {
        compose.setContent {
            VinculoVialTheme {
                WelcomeScreen(onGoToSignIn = {}, onGoToSignUp = {}, onOpenTerms = { termsOpened++ })
            }
        }

        compose.onNodeWithText("Términos y Condiciones y Política de Privacidad")
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, termsOpened)
    }

    // -- Valoración del dueño -----------------------------------------------------------------

    private fun showDetail() {
        compose.setContent {
            VinculoVialTheme {
                InboxDetailScreen(
                    detail = AlertDetailUiState(
                        item = AlertItem(id = "alerta-1", alertType = "CRASHED", createdAt = Instant.EPOCH),
                        evidence = emptyList(),
                    ),
                    onBack = {},
                    onRetryEvidence = {},
                    onRate = {},
                    onReportSender = {},
                    onOpenTerms = { termsOpened++ },
                )
            }
        }
    }

    @Test
    fun `debajo de la valoracion esta la linea sobre el uso de las fotos con el link a los Terminos`() {
        showDetail()

        compose.onNodeWithText(
            "Al enviar tu valoración, aceptás que las fotos redactadas se usen para mejorar la validación automática. Ver Términos",
        ).assertIsDisplayed()
    }

    @Test
    fun `el link de la valoracion abre los Terminos`() {
        showDetail()

        compose.onNodeWithTag(FEEDBACK_TERMS_TAG).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, termsOpened)
    }
}
