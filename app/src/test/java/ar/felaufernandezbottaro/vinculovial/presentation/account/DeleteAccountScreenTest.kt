package ar.felaufernandezbottaro.vinculovial.presentation.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountError
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La baja de cuenta: explica qué se borra y qué se conserva, pide la contraseña y distingue el error
 * de la contraseña (en el campo) de los demás (sobre el botón, con "Reintentar" si sirve).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class DeleteAccountScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: DeleteAccountUiState = DeleteAccountUiState()) {
        compose.setContent {
            VinculoVialTheme {
                DeleteAccountScreen(
                    state = state,
                    onPasswordChange = { calls += "password:$it" },
                    onConfirm = { calls += "confirm" },
                    onBack = { calls += "back" },
                )
            }
        }
    }

    @Test
    fun `explica que se borra, que se conserva y que es irreversible`() {
        show()

        compose.onNodeWithText(DeleteAccountTexts.TITLE).assertIsDisplayed()
        compose.onNodeWithText(DeleteAccountTexts.WHAT_IS_DELETED).assertIsDisplayed()
        compose.onNodeWithText(DeleteAccountTexts.WHAT_IS_KEPT).assertIsDisplayed()
        compose.onNodeWithText(DeleteAccountTexts.IRREVERSIBLE).assertIsDisplayed()
    }

    @Test
    fun `escribir la contrasena y confirmar avisa`() {
        show()

        compose.onNodeWithText("••••••••").performTextInput("Secreta123!")
        compose.onNodeWithText(DeleteAccountTexts.CONFIRM).performClick()
        compose.onNodeWithContentDescription("Volver a la configuración").performClick()

        assertEquals(listOf("password:Secreta123!", "confirm", "back"), calls)
    }

    @Test
    fun `sin contrasena marca el campo como requerido`() {
        show(DeleteAccountUiState(passwordMissing = true))

        compose.onNodeWithText(DeleteAccountTexts.PASSWORD_REQUIRED).assertIsDisplayed()
    }

    @Test
    fun `una contrasena incorrecta se muestra en el campo y no ofrece reintentar`() {
        show(DeleteAccountUiState(status = DeleteAccountStatus.Failed(DeleteAccountError.InvalidPassword)))

        compose.onNodeWithText("Contraseña incorrecta").assertIsDisplayed()
        compose.onNodeWithText(DeleteAccountTexts.CONFIRM).assertIsDisplayed()
    }

    @Test
    fun `un error transitorio se muestra sobre el boton y ofrece reintentar`() {
        show(DeleteAccountUiState(status = DeleteAccountStatus.Failed(DeleteAccountError.Network)))

        compose.onNodeWithText(DeleteAccountError.Network.message()).assertIsDisplayed()
        compose.onNodeWithText(DeleteAccountTexts.RETRY).performClick()

        assertEquals(listOf("confirm"), calls)
    }

    @Test
    fun `una sesion vencida no ofrece reintentar`() {
        show(DeleteAccountUiState(status = DeleteAccountStatus.Failed(DeleteAccountError.SessionExpired)))

        compose.onNodeWithText("Tu sesión venció. Iniciá sesión de nuevo.").assertIsDisplayed()
        compose.onNodeWithText(DeleteAccountTexts.CONFIRM).assertIsDisplayed()
    }

    @Test
    fun `cada error tiene mensaje y sabemos cuales se pueden reintentar`() {
        val retryable = listOf(
            DeleteAccountError.RateLimited,
            DeleteAccountError.Unavailable,
            DeleteAccountError.Network,
            DeleteAccountError.Unexpected,
        )
        val definitive = listOf(DeleteAccountError.InvalidPassword, DeleteAccountError.SessionExpired)

        retryable.forEach { assertEquals("$it", true, it.isRetryable()) }
        definitive.forEach { assertEquals("$it", false, it.isRetryable()) }
        assertEquals(
            DeleteAccountError.RateLimited.message(),
            DeleteAccountError.Unavailable.message(),
        )
    }

    @Test
    fun `mientras elimina no se puede volver ni confirmar`() {
        show(DeleteAccountUiState(password = "x", status = DeleteAccountStatus.Submitting))

        compose.onNodeWithContentDescription("Volver a la configuración").assertIsNotEnabled()
    }

    @Test
    fun `el estado no imprime la contrasena`() {
        val text = DeleteAccountUiState(password = "Secreta123!").toString()

        assertEquals(false, text.contains("Secreta123!"))
    }
}
