package ar.felaufernandezbottaro.vinculovial.data.push

import ar.felaufernandezbottaro.vinculovial.data.inbox.mapAlertTypeToDisplay
import ar.felaufernandezbottaro.vinculovial.presentation.push.PUSH_TYPE_INBOX_UPDATED
import ar.felaufernandezbottaro.vinculovial.presentation.push.PUSH_TYPE_SENT_UPDATED

/**
 * Qué notificación publicar para un mensaje de FCM, ya decidido y sin tocar el framework de
 * Android: [VvcMessagingService] sólo la arma y la publica.
 *
 * @property notificationId un id por alerta: las novedades de una misma alerta se reemplazan entre
 * sí y las de alertas distintas conviven. También es el `requestCode` del `PendingIntent`: con el
 * mismo código, `FLAG_UPDATE_CURRENT` pisaría los extras de la notificación anterior.
 * @property pushTypeExtra el `type` a reenviar a `MainActivity` (`null` en la alerta nueva).
 */
internal data class PushNotificationPlan(
    val title: String,
    val body: String,
    val notificationId: Int,
    val pushTypeExtra: String?,
    val alertType: String?,
    val alertId: String?,
)

internal object PushNotificationPlanner {

    const val NOTIFICATION_ID = 1
    const val INBOX_NOTIFICATION_ID = 2
    const val INBOX_UPDATE_TITLE = "ViVIAna"
    const val INBOX_UPDATE_BODY = "Tenés alertas sin leer en el buzón."
    const val GENERIC_BODY = "Tenés una alerta nueva sobre uno de tus vehículos vinculados."
    const val SENT_UPDATE_TITLE = "ViVIAna"
    const val SENT_UPDATE_BODY = "Tu alerta tuvo novedades."

    /**
     * @param data el bloque `data` del mensaje.
     * @param notificationTitle el título del bloque `notification`, si el mensaje lo trae.
     * @param notificationBody el cuerpo del bloque `notification`, si el mensaje lo trae.
     * @return `null` si el mensaje no es de ninguna de las tres clases de aviso: no se publica nada.
     */
    fun plan(
        data: Map<String, String>,
        notificationTitle: String?,
        notificationBody: String?,
    ): PushNotificationPlan? {
        val alertId = data[EXTRA_ALERT_ID]?.takeIf { it.isNotBlank() }
        val pushType = data[EXTRA_PUSH_TYPE]
        val isSentUpdate = pushType == PUSH_TYPE_SENT_UPDATED
        val isInboxUpdate = pushType == PUSH_TYPE_INBOX_UPDATED
        val alertType = data[EXTRA_ALERT_TYPE]
        if (!isSentUpdate && !isInboxUpdate && alertType == null) return null

        val title: String
        val body: String
        when {
            isSentUpdate -> {
                title = notificationTitle ?: SENT_UPDATE_TITLE
                body = notificationBody ?: SENT_UPDATE_BODY
            }
            isInboxUpdate -> {
                title = notificationTitle ?: INBOX_UPDATE_TITLE
                body = notificationBody ?: INBOX_UPDATE_BODY
            }
            else -> {
                title = mapAlertTypeToDisplay(alertType.orEmpty()).label
                body = GENERIC_BODY
            }
        }

        // El aviso de alertas sin leer no trae `alert_id` y tiene su propio id, para no pisar a los demás.
        val notificationId = alertId?.hashCode() ?: if (isInboxUpdate) INBOX_NOTIFICATION_ID else NOTIFICATION_ID

        return PushNotificationPlan(
            title = title,
            body = body,
            notificationId = notificationId,
            pushTypeExtra = when {
                isSentUpdate -> PUSH_TYPE_SENT_UPDATED
                isInboxUpdate -> PUSH_TYPE_INBOX_UPDATED
                else -> null
            },
            alertType = alertType,
            alertId = alertId,
        )
    }
}
