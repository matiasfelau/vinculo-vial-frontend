package ar.felaufernandezbottaro.vinculovial.presentation.scan

import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidencePlan
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderChatTest {

    private fun completedEvidence(alertType: AlertType, details: Int = 1): EvidenceCaptureState {
        val steps = EvidencePlan.stepsFor(alertType)
        return EvidenceCaptureState(
            steps = steps,
            stepIndex = steps.lastIndex,
            photoIndex = if (alertType == AlertType.CRASHED) details - 1 else 0,
            phase = EvidencePhase.COMPLETE,
        )
    }

    private fun stateOf(
        alertType: AlertType?,
        status: ScanStatus = ScanStatus.AwaitingAlertType,
        evidence: EvidenceCaptureState = EvidenceCaptureState(),
    ) = ScanUiState(status = status, selectedAlertType = alertType, evidence = evidence)

    @Test
    fun `antes de elegir ViVIAna saluda y pide que elija uno de los mensajes`() {
        val items = senderChatItems(stateOf(alertType = null))

        assertEquals(
            listOf(
                SenderChatItem.Vivi("Hola, soy ViVIAna. Ya identifiqué el vehículo."),
                SenderChatItem.Vivi("¿Qué le querés avisar a su dueño? Elegí uno de estos mensajes."),
            ),
            items,
        )
    }

    @Test
    fun `elegir un mensaje lo muestra como mensaje del usuario y ViVIAna pide las fotos`() {
        val items = senderChatItems(
            stateOf(
                alertType = AlertType.CRASHED,
                evidence = EvidenceCaptureState(steps = EvidencePlan.stepsFor(AlertType.CRASHED), phase = EvidencePhase.CAPTURING),
            ),
        )

        assertEquals(SenderChatItem.User("Tu auto fue chocado."), items[1])
        assertTrue((items[2] as SenderChatItem.Vivi).text.startsWith("Para avisarle necesito fotos."))
        assertEquals(3, items.size)
    }

    @Test
    fun `con las fotos listas se muestran como adjunto del usuario`() {
        val items = senderChatItems(stateOf(AlertType.CRASHED, evidence = completedEvidence(AlertType.CRASHED, details = 2)))

        val attachment = items.last() as SenderChatItem.Attachment
        assertEquals(listOf("General", "Patente", "Daño 1", "Daño 2"), attachment.labels)
        assertEquals("4 fotos, cifradas en tu teléfono", attachment.summary)
        assertEquals(false, attachment.sent)
    }

    @Test
    fun `las fotos de PARKED_ON_GARAGE son general, patente y contexto`() {
        val items = senderChatItems(stateOf(AlertType.PARKED_ON_GARAGE, evidence = completedEvidence(AlertType.PARKED_ON_GARAGE)))

        val attachment = items.last() as SenderChatItem.Attachment
        assertEquals(listOf("General", "Patente", "Contexto"), attachment.labels)
        assertEquals("3 fotos, cifradas en tu teléfono", attachment.summary)
    }

    @Test
    fun `una sola foto de daño no lleva numero`() {
        val state = completedEvidence(AlertType.CRASHED, details = 1)

        assertEquals(listOf("General", "Patente", "Daño"), state.photoLabels())
    }

    @Test
    fun `mientras se envia ViVIAna esta escribiendo`() {
        val items = senderChatItems(
            stateOf(AlertType.CRASHED, status = ScanStatus.SendingAlert, evidence = completedEvidence(AlertType.CRASHED)),
        )

        assertEquals(SenderChatItem.Typing, items.last())
    }

    @Test
    fun `tras el 202 el adjunto queda enviado y ViVIAna confirma la recepcion`() {
        val items = senderChatItems(
            stateOf(AlertType.CRASHED, status = ScanStatus.AlertSent, evidence = completedEvidence(AlertType.CRASHED)),
        )

        assertEquals(true, (items[items.size - 2] as SenderChatItem.Attachment).sent)
        assertEquals(
            SenderChatItem.Vivi("Recibí todo. El sistema va a validar la información y, si la aprueba, le aviso al dueño."),
            items.last(),
        )
    }

    @Test
    fun `el envio incierto es un aviso de ViVIAna en ambar`() {
        val items = senderChatItems(
            stateOf(
                AlertType.CRASHED,
                status = ScanStatus.AlertFailed(SendAlertError.PossiblySent),
                evidence = completedEvidence(AlertType.CRASHED),
            ),
        )

        assertEquals(
            SenderChatItem.Vivi("No pudimos confirmar el envío. Puede que tu alerta se haya enviado.", isWarning = true),
            items.last(),
        )
    }

    @Test
    fun `un error del envio es un aviso ambar de ViVIAna con su mensaje`() {
        val items = senderChatItems(
            stateOf(
                AlertType.CRASHED,
                status = ScanStatus.AlertFailed(SendAlertError.Unavailable),
                evidence = completedEvidence(AlertType.CRASHED),
            ),
        )

        val warning = items.last() as SenderChatItem.Vivi
        assertEquals(true, warning.isWarning)
        assertEquals(SendAlertError.Unavailable.message(), warning.text)
    }

    @Test
    fun `la conversacion nunca incluye texto libre del usuario`() {
        val items = senderChatItems(stateOf(AlertType.PARKED_ON_GARAGE, evidence = completedEvidence(AlertType.PARKED_ON_GARAGE)))

        val userTexts = items.filterIsInstance<SenderChatItem.User>().map { it.text }
        assertEquals(listOf("Tu auto está bloqueando una rampa (garage o accesibilidad)."), userTexts)
    }
}
