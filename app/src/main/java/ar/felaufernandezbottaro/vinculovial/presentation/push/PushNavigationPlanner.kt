package ar.felaufernandezbottaro.vinculovial.presentation.push

import ar.felaufernandezbottaro.vinculovial.presentation.navigation.Route

/**
 * Decide qué stack de rutas reconstruir al abrir la app desde una notificación push. Pura y
 * sin `NavController` de por medio para poder probarla sin Compose: ni `alert_type` ni
 * `alert_id` se interpretan acá, así que un valor desconocido produce exactamente el mismo plan
 * que uno conocido, nunca una excepción. El chat concreto lo abre después el buzón, cuando ya
 * tiene la alerta cargada.
 *
 * [hasSession] es "la sesión está desbloqueada": con la app bloqueada (US-12) el plan es vacío, el
 * push queda pendiente y se aplica recién cuando el desbloqueo termina.
 *
 * La Bandeja de Entrada nunca queda sola en el stack: siempre va precedida por Settings, el
 * home temporal de la app -- así "atrás" lleva ahí, nunca cierra la app.
 */
fun planPushNavigation(hasSession: Boolean, target: PushTarget?): List<String> = when {
    !hasSession || target == null -> emptyList()
    else -> listOf(Route.SETTINGS, Route.INBOX)
}
