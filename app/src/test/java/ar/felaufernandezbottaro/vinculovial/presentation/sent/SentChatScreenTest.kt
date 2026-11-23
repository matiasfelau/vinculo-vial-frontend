package ar.felaufernandezbottaro.vinculovial.presentation.sent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlert
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlertStatus
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** El chat de sólo lectura de una alerta enviada: lo que cuenta ViVIAna según el estado. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class SentChatScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var backs = 0

    private fun show(status: SentAlertStatus, seenAt: Instant? = null) {
        val alert = SentAlert(
            id = "s-1",
            alertType = "CRASHED",
            createdAt = Instant.now().minusSeconds(3600),
            status = status,
            seenAt = seenAt,
        )
        compose.setContent { VinculoVialTheme { SentChatScreen(alert = alert, onBack = { backs++ }) } }
    }

    @Test
    fun `una alerta validada muestra el recibo y la validacion`() {
        show(SentAlertStatus.ACTIVE)

        compose.onNodeWithText(ViviMessages.SENDER_SENT_RECEIPT).assertIsDisplayed()
        compose.onNodeWithText(ViviMessages.SENDER_VALIDATED).assertIsDisplayed()
        compose.onNodeWithText(ViviMessages.SENDER_FIXED_NOTE).assertIsDisplayed()
    }

    @Test
    fun `una alerta leida suma el aviso de lectura`() {
        show(SentAlertStatus.ACTIVE, seenAt = Instant.now())

        compose.onNodeWithText(ViviMessages.SENDER_SEEN).assertIsDisplayed()
    }

    @Test
    fun `una alerta rechazada lo dice`() {
        show(SentAlertStatus.REJECTED)

        compose.onNodeWithText(ViviMessages.SENDER_REJECTED).assertIsDisplayed()
    }

    @Test
    fun `una alerta vencida sin respuesta lo dice`() {
        show(SentAlertStatus.EXPIRED)

        compose.onNodeWithText(ViviMessages.SENDER_NO_RESPONSE).assertIsDisplayed()
    }

    @Test
    fun `volver avisa`() {
        show(SentAlertStatus.PENDING_VALIDATION)

        compose.onNodeWithContentDescription("Volver al buzón").performClick()

        assertEquals(1, backs)
    }
}
