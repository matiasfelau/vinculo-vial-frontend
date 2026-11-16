package ar.felaufernandezbottaro.vinculovial.presentation.scan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidenceRole
import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidenceStep
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.senderMessage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * El chat del emisor con ViVIAna: el menú de mensajes fijos, el paso de las fotos, el envío y qué
 * botón queda según cómo terminó (reintentar, volver a empezar o cerrar).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class ReportChatScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val steps = listOf(EvidenceStep(EvidenceRole.GENERAL, "foto"), EvidenceStep(EvidenceRole.PLATE, "patente"))

    private fun state(
        status: ScanStatus = ScanStatus.AwaitingAlertType,
        selected: AlertType? = null,
        phase: EvidencePhase = EvidencePhase.IDLE,
        stepIndex: Int = 0,
    ) = ScanUiState(
        status = status,
        selectedAlertType = selected,
        evidence = EvidenceCaptureState(steps = steps, stepIndex = stepIndex, phase = phase),
    )

    private fun show(state: ScanUiState) {
        compose.setContent {
            VinculoVialTheme {
                ReportChatScreen(
                    uiState = state,
                    onAlertTypeSelected = { calls += "type:${it.name}" },
                    onStartCapture = { calls += "capture" },
                    onReportRequested = { calls += "send" },
                    onRetry = { calls += "retry" },
                    onRestart = { calls += "restart" },
                    onFinished = { calls += "finished" },
                    onCancel = { calls += "cancel" },
                )
            }
        }
    }

    @Test
    fun `ViVIAna saluda y ofrece solo los mensajes con validacion`() {
        show(state())

        compose.onNodeWithText(ViviMessages.SENDER_GREETING).assertIsDisplayed()
        compose.onNodeWithText(ViviMessages.SENDER_PROMPT).assertIsDisplayed()
        compose.onNodeWithText(AlertType.CRASHED.senderMessage()).assertIsDisplayed()
        compose.onNodeWithText(AlertType.PARKED_ON_GARAGE.senderMessage()).assertIsDisplayed()
        compose.onAllNodesWithText(AlertType.PARKED_TOO_CLOSE.senderMessage()).assertCountEquals(0)
    }

    @Test
    fun `elegir un mensaje avisa cual`() {
        show(state())

        compose.onNodeWithText(AlertType.CRASHED.senderMessage()).performClick()

        assertEquals(listOf("type:CRASHED"), calls)
    }

    @Test
    fun `con el mensaje elegido ViVIAna pide las fotos y ofrece tomarlas`() {
        show(state(selected = AlertType.CRASHED, phase = EvidencePhase.READY))

        compose.onNodeWithText(ViviMessages.SENDER_PHOTOS_INTRO).assertIsDisplayed()
        compose.onNodeWithText(ViviMessages.SENDER_TAKE_PHOTOS).performClick()

        assertEquals(listOf("capture"), calls)
    }

    @Test
    fun `con las fotos completas muestra el adjunto y permite enviar`() {
        show(state(selected = AlertType.CRASHED, phase = EvidencePhase.COMPLETE, stepIndex = 1))

        compose.onNodeWithText(ViviMessages.senderAttachmentSummary(2)).assertIsDisplayed()
        compose.onNodeWithText("Enviar").performClick()

        assertEquals(listOf("send"), calls)
    }

    @Test
    fun `mientras envia ViVIAna escribe y no se puede cancelar`() {
        show(state(status = ScanStatus.SendingAlert, selected = AlertType.CRASHED, phase = EvidencePhase.COMPLETE))

        compose.onNodeWithContentDescription("Cancelar").assertIsNotEnabled()
    }

    @Test
    fun `enviado muestra el recibo y cierra`() {
        show(state(status = ScanStatus.AlertSent, selected = AlertType.CRASHED, phase = EvidencePhase.COMPLETE))

        compose.onNodeWithText(ViviMessages.SENDER_SENT_RECEIPT).assertIsDisplayed()
        compose.onNodeWithText("Enviado").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()

        assertEquals(listOf("finished"), calls)
    }

    @Test
    fun `un cupo diario ocupado lo explica y solo deja cerrar`() {
        show(state(status = ScanStatus.DailyLimitReached(Instant.parse("2026-11-17T12:00:00Z"))))

        compose.onNodeWithText("Cerrar").performClick()

        assertEquals(listOf("cancel"), calls)
    }

    @Test
    fun `una falla transitoria se reintenta`() {
        show(state(status = ScanStatus.AlertFailed(SendAlertError.Network), selected = AlertType.CRASHED, phase = EvidencePhase.COMPLETE))

        compose.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `un intento perdido ofrece volver a empezar`() {
        show(state(status = ScanStatus.AlertFailed(SendAlertError.AttemptNotFound), selected = AlertType.CRASHED, phase = EvidencePhase.COMPLETE))

        compose.onNodeWithText("Volver a empezar").performClick()

        assertEquals(listOf("restart"), calls)
    }

    @Test
    fun `una falla sin arreglo solo permite cerrar`() {
        show(state(status = ScanStatus.AlertFailed(SendAlertError.InvalidEvidence), selected = AlertType.CRASHED, phase = EvidencePhase.COMPLETE))

        compose.onNodeWithText("Cerrar").performClick()

        assertEquals(listOf("cancel"), calls)
    }

    @Test
    fun `volver abre la confirmacion y confirmarla cancela el reporte`() {
        show(state())

        compose.onNodeWithContentDescription("Cancelar").performClick()
        compose.onNodeWithText("¿Deseás cancelar el reporte?").assertIsDisplayed()
        compose.onNodeWithText("Sí, cancelar").performClick()

        assertEquals(listOf("cancel"), calls)
    }

    @Test
    fun `seguir en la confirmacion no cancela`() {
        show(state())

        compose.onNodeWithContentDescription("Cancelar").performClick()
        compose.onNodeWithText("Seguir").performClick()

        assertEquals(emptyList<String>(), calls)
        compose.onAllNodesWithText("¿Deseás cancelar el reporte?").assertCountEquals(0)
    }
}
