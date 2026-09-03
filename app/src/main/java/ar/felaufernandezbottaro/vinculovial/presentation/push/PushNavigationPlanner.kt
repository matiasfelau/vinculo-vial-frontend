package ar.felaufernandezbottaro.vinculovial.presentation.push

import ar.felaufernandezbottaro.vinculovial.presentation.navigation.Route

/**
 * Decide qué stack de rutas reconstruir al abrir la app desde una notificación push. Pura y
 * sin `NavController` de por medio para poder probarla sin Compose: `alert_type` no se
 * interpreta acá (ni en ningún otro lado -- ver `mapAlertTypeToDisplay`), así que un valor
 * desconocido produce exactamente el mismo plan que uno conocido, nunca una excepción.
 *
 * La Bandeja de Entrada nunca queda sola en el stack: siempre va precedida por Settings, el
 * home temporal de la app -- así "atrás" lleva ahí, nunca cierra la app.
 */
fun planPushNavigation(hasSession: Boolean, pendingAlertType: String?): List<String> = when {
    !hasSession || pendingAlertType == null -> emptyList()
    else -> listOf(Route.SETTINGS, Route.INBOX)
}
