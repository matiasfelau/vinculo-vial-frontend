package ar.felaufernandezbottaro.vinculovial.presentation.scan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptResult
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Lo que se ve y lo que dispara el escaneo de patente sobre la cámara: el texto de cada estado,
 * los diálogos de confirmación, rechazo, ingreso manual y error, y la linterna. El `PreviewView`
 * es el único hueco que queda afuera, porque necesita cámara real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class ScanPlateOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var torchOn = false

    private fun show(status: ScanStatus, failure: String? = null, torch: Boolean = false, selected: Boolean = false) {
        compose.setContent {
            VinculoVialTheme {
                ScanPlateOverlay(
                    uiState = ScanUiState(status = status),
                    failure = failure,
                    torchOn = torch,
                    onTorchToggle = { calls += "torch" },
                    onPlateConfirmed = { calls += "confirmed" },
                    onPlateRejected = { calls += "rejected" },
                    onRescan = { calls += "rescan" },
                    onManualEntryOpened = { calls += "manual" },
                    onManualPlateSubmitted = { calls += "submitted:$it" },
                    onManualEntryDismissed = { calls += "manualDismissed" },
                    onCheckErrorDismissed = { calls += "errorDismissed" },
                    onCancel = { calls += "cancel" },
                    preview = { Box(Modifier.fillMaxSize()) },
                )
            }
        }
    }

    @Test
    fun `escaneando pide encuadrar la patente`() {
        show(ScanStatus.Scanning)

        compose.onNodeWithText("Encuadrá la patente del vehículo").assertIsDisplayed()
    }

    @Test
    fun `cada estado tiene su titular`() {
        assertEquals("¿Es correcta esta patente?", statusHeadline(ScanStatus.Detected("ABC123")))
        assertEquals("¿Cómo querés seguir?", statusHeadline(ScanStatus.Rejected))
        assertEquals("Ingresá la patente", statusHeadline(ScanStatus.ManualEntry()))
        assertEquals("Preparando el reporte…", statusHeadline(ScanStatus.Checking))
        assertEquals("Elegí qué querés reportar", statusHeadline(ScanStatus.AwaitingAlertType))
        assertEquals(
            "Ya enviaste una alerta para esta patente",
            statusHeadline(ScanStatus.DailyLimitReached(Instant.EPOCH)),
        )
        assertEquals("Enviando el reporte…", statusHeadline(ScanStatus.SendingAlert))
        assertEquals("¡Listo!", statusHeadline(ScanStatus.AlertSent))
        assertEquals(
            "No pudimos preparar el reporte",
            statusHeadline(ScanStatus.CheckFailed(StartAlertAttemptResult.Unexpected)),
        )
    }

    @Test
    fun `una falla de la camara reemplaza al titular`() {
        show(ScanStatus.Scanning, failure = "No pudimos abrir la cámara.")

        compose.onNodeWithText("No pudimos abrir la cámara.").assertIsDisplayed()
        compose.onNodeWithText("Encuadrá la patente del vehículo").assertDoesNotExist()
    }

    @Test
    fun `cancelar y la linterna disparan sus callbacks`() {
        show(ScanStatus.Scanning)

        compose.onNodeWithContentDescription("Encender la luz").performClick()
        compose.onNodeWithContentDescription("Cancelar").performClick()

        assertEquals(listOf("torch", "cancel"), calls)
    }

    @Test
    fun `con la linterna prendida ofrece apagarla`() {
        show(ScanStatus.Scanning, torch = true)

        compose.onNodeWithContentDescription("Apagar la luz").assertIsDisplayed()
    }

    @Test
    fun `mientras chequea no se puede cancelar`() {
        show(ScanStatus.Checking)

        compose.onNodeWithContentDescription("Cancelar").assertIsNotEnabled()
    }

    @Test
    fun `fuera de un chequeo se puede cancelar`() {
        show(ScanStatus.Scanning)

        compose.onNodeWithContentDescription("Cancelar").assertIsEnabled()
    }

    @Test
    fun `una patente detectada se confirma o se rechaza`() {
        show(ScanStatus.Detected("AB123CD"))

        compose.onNodeWithText("Patente detectada: AB123CD. ¿Es correcto?").assertIsDisplayed()
        compose.onNodeWithText("Sí, es correcta").performClick()
        compose.onNodeWithText("No").performClick()

        assertEquals(listOf("confirmed", "rejected"), calls)
    }

    @Test
    fun `una patente rechazada ofrece reescanear o ingresarla a mano`() {
        show(ScanStatus.Rejected)

        compose.onNodeWithText("Podés volver a encuadrar la patente o escribirla vos mismo.").assertIsDisplayed()
        compose.onNodeWithText("Ingresar manualmente").performClick()
        compose.onNodeWithText("Volver a escanear").performClick()

        assertEquals(listOf("manual", "rescan"), calls)
    }

    @Test
    fun `una falla del chequeo muestra el mensaje y se descarta`() {
        show(ScanStatus.CheckFailed(StartAlertAttemptResult.Unavailable))

        compose.onNodeWithText("Entendido").performClick()

        assertEquals(listOf("errorDismissed"), calls)
    }

    @Test
    fun `sin estado de dialogo no hay dialogos`() {
        show(ScanStatus.Scanning)

        compose.onNodeWithText("Sí, es correcta").assertDoesNotExist()
        compose.onNodeWithText("Ingresar manualmente").assertDoesNotExist()
        compose.onNodeWithText("Verificar").assertDoesNotExist()
    }
}
