package ar.felaufernandezbottaro.vinculovial.presentation.push

/**
 * A dónde lleva una notificación push, ya interpretada. [alertId] es opcional en los dos casos:
 * sin él, la notificación sólo lleva a la lista correspondiente.
 */
sealed interface PushTarget {

    /** Alerta nueva sobre un vehículo del usuario: abre el chat del dueño (o el buzón sin `alert_id`). */
    data class NewAlert(val alertType: String, val alertId: String?) : PushTarget

    /** `data.type = SENT_UPDATED`: cambió el estado de una alerta que envió el usuario. Abre el chat del emisor. */
    data class SentUpdated(val alertId: String?) : PushTarget
}

/** Valor de `data.type` de los avisos al emisor. */
const val PUSH_TYPE_SENT_UPDATED = "SENT_UPDATED"

/**
 * Interpreta los extras de la notificación. `alert_type` y `alert_id` no se validan ni se
 * interpretan acá: un valor desconocido produce el mismo destino que uno conocido, nunca una
 * excepción. Un `alert_id` en blanco cuenta como ausente.
 */
fun parsePushTarget(type: String?, alertType: String?, alertId: String?): PushTarget? {
    val id = alertId?.takeIf { it.isNotBlank() }

    return when {
        type == PUSH_TYPE_SENT_UPDATED -> PushTarget.SentUpdated(id)
        alertType != null -> PushTarget.NewAlert(alertType, id)
        else -> null
    }
}
