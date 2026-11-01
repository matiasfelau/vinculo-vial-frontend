package ar.felaufernandezbottaro.vinculovial.data.push

/**
 * Clave de `alert_type`, tanto en el `data` payload de FCM como en el extra del intent que
 * abre `MainActivity` al tocar la notificación. Tiene que ser la misma clave literal a
 * propósito: si el mensaje trae además un bloque `notification` -- lo manda así, por ejemplo,
 * el compositor de prueba de la consola de Firebase, aunque el contrato real (ver CLAUDE.md)
 * sea `data` puro -- el sistema operativo muestra la notificación y resuelve el tap por su
 * cuenta cuando la app está en segundo plano o cerrada. `VvcMessagingService.onMessageReceived`
 * nunca corre en ese caso, así que el `PendingIntent` propio tampoco se construye: es el SDK
 * de FCM el que arma el intent de apertura, y reenvía el `data` original tal cual, con sus
 * claves originales. Si `MainActivity` buscara el extra bajo un nombre propio en vez de
 * `alert_type`, la app abriría igual pero nunca encontraría el valor en ese camino -- la app
 * "abre pero no navega" que describe el reporte que motivó este fix.
 */
const val EXTRA_ALERT_TYPE = "alert_type"

/**
 * `data.type` del push. Igual que [EXTRA_ALERT_TYPE], la misma clave literal en el payload FCM y
 * en el extra del intent: los avisos al emisor llevan además un bloque `notification`, así que
 * cuando la app está en segundo plano es el sistema quien resuelve el tap y reenvía el `data`
 * original con sus claves originales.
 */
const val EXTRA_PUSH_TYPE = "type"

/** `data.alert_id` del push: la alerta a abrir. Opcional en los avisos a dueños. */
const val EXTRA_ALERT_ID = "alert_id"
