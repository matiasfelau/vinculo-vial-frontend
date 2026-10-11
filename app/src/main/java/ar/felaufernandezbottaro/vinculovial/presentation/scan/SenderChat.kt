package ar.felaufernandezbottaro.vinculovial.presentation.scan

import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.senderMessage

/** Un elemento de la conversación del emisor con ViVIAna. */
sealed interface SenderChatItem {

    /** Un mensaje de ViVIAna. [isWarning] lo pinta ámbar: un error del envío. */
    data class Vivi(val text: String, val isWarning: Boolean = false) : SenderChatItem

    /** El mensaje fijo que eligió el emisor. */
    data class User(val text: String) : SenderChatItem

    /** Las fotos como un único adjunto del usuario: un candado y "N fotos cifradas adjuntas", sin detalle por foto. */
    data class Attachment(val photoCount: Int, val summary: String, val sent: Boolean) : SenderChatItem

    /** ViVIAna "escribiendo" mientras se sube el envío. */
    data object Typing : SenderChatItem
}

/**
 * La conversación del emisor según el estado. Todo lo que dice ViVIAna son plantillas de
 * [ViviMessages]: ni un texto libre del usuario ni nada que venga del servidor.
 */
fun senderChatItems(state: ScanUiState): List<SenderChatItem> = buildList {
    add(SenderChatItem.Vivi(ViviMessages.SENDER_GREETING))

    val alertType = state.selectedAlertType
    if (alertType == null) {
        add(SenderChatItem.Vivi(ViviMessages.SENDER_PROMPT))
        return@buildList
    }

    add(SenderChatItem.User(alertType.senderMessage()))
    add(SenderChatItem.Vivi(ViviMessages.senderPhotosIntro(alertType)))

    if (state.evidence.phase == EvidencePhase.COMPLETE) {
        val photoCount = state.evidence.photoCount()
        add(
            SenderChatItem.Attachment(
                photoCount = photoCount,
                summary = ViviMessages.senderAttachmentSummary(photoCount),
                sent = state.status is ScanStatus.AlertSent,
            ),
        )
    }

    when (val status = state.status) {
        ScanStatus.SendingAlert -> add(SenderChatItem.Typing)
        ScanStatus.AlertSent -> add(SenderChatItem.Vivi(ViviMessages.SENDER_SENT_RECEIPT))
        is ScanStatus.AlertFailed -> add(SenderChatItem.Vivi(status.error.message(), isWarning = true))
        else -> Unit
    }
}
