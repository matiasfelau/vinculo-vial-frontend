package ar.felaufernandezbottaro.vinculovial.presentation.scan

import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidencePlan
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderChatTest {

    private fun completedEvidence(alertType: AlertType): EvidenceCaptureState {
        val steps = EvidencePlan.stepsFor(alertType)
        return EvidenceCaptureState(steps = steps, stepIndex = steps.lastIndex, photoIndex = 0, phase = EvidencePhase.COMPLETE)
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

    private fun readyEvidence(alertType: AlertType) =
        EvidenceCaptureState(steps = EvidencePlan.stepsFor(alertType), phase = EvidencePhase.READY)

    @Test
    fun `elegir un mensaje lo muestra como mensaje del usuario y ViVIAna explica que fotos hacen falta`() {
        val items = senderChatItems(stateOf(AlertType.CRASHED, evidence = readyEvidence(AlertType.CRASHED)))

        assertEquals(SenderChatItem.User("Tu auto fue chocado."), items[1])
        assertEquals(
            SenderChatItem.Vivi(
                "Para avisarle necesito fotos. Te guío con la cámara: el auto con la patente visible, " +
                    "la patente de cerca y una foto del daño.",
            ),
            items[2],
        )
        assertEquals(3, items.size)
    }

    @Test
    fun `mientras el usuario lee la explicacion no hay adjunto ni indicio de camara`() {
        val items = senderChatItems(stateOf(AlertType.PARKED_ON_GARAGE, evidence = readyEvidence(AlertType.PARKED_ON_GARAGE)))

        assertTrue(items.none { it is SenderChatItem.Attachment })
        assertTrue(items.none { it is SenderChatItem.Typing })
        assertTrue(items.filterIsInstance<SenderChatItem.Vivi>().none { it.isWarning })
    }

    @Test
    fun `tras volver de la camara la conversacion es la misma que antes de abrirla`() {
        val before = senderChatItems(stateOf(AlertType.CRASHED, evidence = readyEvidence(AlertType.CRASHED)))
        val capturing = senderChatItems(
            stateOf(AlertType.CRASHED, evidence = readyEvidence(AlertType.CRASHED).copy(phase = EvidencePhase.CAPTURING)),
        )

        assertEquals(before, capturing)
    }

    @Test
    fun `con las fotos listas se muestran como una sola burbuja de adjunto`() {
        val items = senderChatItems(stateOf(AlertType.CRASHED, evidence = completedEvidence(AlertType.CRASHED)))

        val attachments = items.filterIsInstance<SenderChatItem.Attachment>()
        assertEquals(1, attachments.size)
        assertEquals("3 fotos cifradas adjuntas", attachments.single().summary)
        assertEquals(false, attachments.single().sent)
    }

    @Test
    fun `el adjunto cuenta 3 fotos en todos los tipos, CRASHED pide una sola de daño`() {
        assertEquals(3, completedEvidence(AlertType.CRASHED).photoCount())
        assertEquals(3, completedEvidence(AlertType.PARKED_ON_GARAGE).photoCount())
    }

    @Test
    fun `PARKED_ON_GARAGE adjunta 3 fotos cifradas`() {
        val items = senderChatItems(stateOf(AlertType.PARKED_ON_GARAGE, evidence = completedEvidence(AlertType.PARKED_ON_GARAGE)))

        val attachment = items.last() as SenderChatItem.Attachment
        assertEquals(3, attachment.photoCount)
        assertEquals("3 fotos cifradas adjuntas", attachment.summary)
    }

    @Test
    fun `sin captura el conteo de fotos es cero`() {
        assertEquals(0, EvidenceCaptureState().photoCount())
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
