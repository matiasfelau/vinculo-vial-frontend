package ar.felaufernandezbottaro.vinculovial.presentation.signup

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.SignUpForm
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El formulario del alta con la casilla de Términos (US-19): sin marcarla no se puede seguir, y el
 * texto de la casilla lleva a la pantalla de Términos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class SignUpFormScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var validations = 0
    private val accepted = mutableListOf<Boolean>()
    private var termsOpened = 0

    private fun show(termsAccepted: Boolean, submitting: Boolean = false) {
        val form = SignUpForm(
            dni = "12345678",
            email = "usuario@ejemplo.com",
            password = "Password123!",
            passwordConfirmation = "Password123!",
            termsAccepted = termsAccepted,
        )
        compose.setContent {
            VinculoVialTheme {
                SignUpFormScreen(
                    state = SignUpUiState(
                        form = form,
                        status = if (submitting) SignUpStatus.Submitting else SignUpStatus.Idle,
                    ),
                    onDniChange = {},
                    onEmailChange = {},
                    onPasswordChange = {},
                    onPasswordConfirmationChange = {},
                    onValidateIdentity = { validations++ },
                    onGoToLogin = {},
                    onTermsAcceptedChange = { accepted += it },
                    onOpenTerms = { termsOpened++ },
                )
            }
        }
    }

    @Test
    fun `la casilla dice Lei y acepto los Terminos y Condiciones y la Politica de Privacidad`() {
        show(termsAccepted = false)

        compose.onNodeWithTag(TERMS_LINK_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Leí y acepto los Términos y Condiciones y la Política de Privacidad").assertIsDisplayed()
    }

    @Test
    fun `sin la casilla marcada el boton para seguir esta deshabilitado`() {
        show(termsAccepted = false)

        compose.onNodeWithTag(TERMS_CHECKBOX_TAG).performScrollTo().assertIsOff()
        compose.onNodeWithText("Validar identidad").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `sin la casilla marcada tocar el boton no avanza`() {
        show(termsAccepted = false)

        compose.onNodeWithText("Validar identidad").performScrollTo().performClick()

        assertEquals(0, validations)
    }

    @Test
    fun `con la casilla marcada el boton se habilita y avanza`() {
        show(termsAccepted = true)

        compose.onNodeWithTag(TERMS_CHECKBOX_TAG).performScrollTo().assertIsOn()
        compose.onNodeWithText("Validar identidad").performScrollTo().assertIsEnabled().performClick()

        assertEquals(1, validations)
    }

    @Test
    fun `tocar la casilla sin marcar avisa que se acepto`() {
        show(termsAccepted = false)

        compose.onNodeWithTag(TERMS_CHECKBOX_TAG).performScrollTo().performClick()

        assertEquals(listOf(true), accepted)
    }

    @Test
    fun `desmarcar una casilla marcada avisa false`() {
        show(termsAccepted = true)

        compose.onNodeWithTag(TERMS_CHECKBOX_TAG).performScrollTo().performClick()

        assertEquals(listOf(false), accepted)
    }

    @Test
    fun `el texto de la casilla abre la pantalla de Terminos sin marcarla`() {
        show(termsAccepted = false)

        compose.onNodeWithTag(TERMS_LINK_TAG).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, termsOpened)
        assertEquals(emptyList<Boolean>(), accepted)
    }

    @Test
    fun `mientras se envia la casilla no responde`() {
        show(termsAccepted = true, submitting = true)

        compose.onNodeWithTag(TERMS_CHECKBOX_TAG).performScrollTo().assertIsNotEnabled()
    }
}
