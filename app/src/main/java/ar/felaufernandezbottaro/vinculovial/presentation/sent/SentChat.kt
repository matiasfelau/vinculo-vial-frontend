package ar.felaufernandezbottaro.vinculovial.presentation.sent

import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlert
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlertState
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.toChatDayStamp
import ar.felaufernandezbottaro.vinculovial.presentation.scan.SenderChatItem
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.senderMessage
import java.time.Instant
import java.time.ZoneId

/**
 * La conversación del emisor reconstruida sólo desde el estado de la alerta: el mensaje que
 * eligió, el "Recibí todo…" y lo que ViVIAna agrega según el estado. Ninguna foto, veredicto,
 * motivo ni dato del dueño: el contrato de Enviados no los trae.
 *
 * Con la alerta leída se muestran dos mensajes de ViVIAna: que la validó y que el dueño la
 * leyó, éste con el día y la hora de `seen_at`.
 */
fun sentChatItems(
    alert: SentAlert,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<SenderChatItem> = buildList {
    AlertType.entries.firstOrNull { it.wireValue == alert.alertType }
        ?.let { add(SenderChatItem.User(it.senderMessage())) }

    add(SenderChatItem.Vivi(ViviMessages.SENDER_SENT_RECEIPT))

    when (alert.state) {
        SentAlertState.IN_VALIDATION -> Unit
        SentAlertState.VALIDATED -> add(SenderChatItem.Vivi(ViviMessages.SENDER_VALIDATED))
        SentAlertState.SEEN -> {
            add(SenderChatItem.Vivi(ViviMessages.SENDER_VALIDATED))
            add(SenderChatItem.Vivi(ViviMessages.SENDER_SEEN, time = alert.seenAt?.toChatDayStamp(now, zone)))
        }
        SentAlertState.REJECTED -> add(SenderChatItem.Vivi(ViviMessages.SENDER_REJECTED, isWarning = true))
        SentAlertState.NO_RESPONSE -> add(SenderChatItem.Vivi(ViviMessages.SENDER_NO_RESPONSE, isWarning = true))
    }
}
