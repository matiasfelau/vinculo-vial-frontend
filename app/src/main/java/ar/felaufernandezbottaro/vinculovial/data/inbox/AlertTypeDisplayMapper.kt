package ar.felaufernandezbottaro.vinculovial.data.inbox

/**
 * Clasificación de ícono, independiente de Compose: `presentation` la traduce a un
 * `ImageVector` concreto. [GENERIC] es la clasificación de un `alert_type` no reconocido.
 */
enum class AlertTypeIconKind {
    LUCES_ENCENDIDAS,
    PARKED_ON_GARAGE,
    PARKED_TOO_CLOSE,
    CRASHED,
    GENERIC,
}

data class AlertTypeDisplay(val label: String, val icon: AlertTypeIconKind)

/**
 * Traduce el `alert_type` crudo del backend a un ícono y una etiqueta legible. El `when` sin
 * `enumValueOf`/`valueOf` es deliberado: un valor no reconocido cae en la rama `else` con un
 * ícono genérico, nunca lanza una excepción que rompa el render de la lista completa -- el
 * catálogo de incidentes puede crecer del lado del backend antes que esta app se actualice.
 */
fun mapAlertTypeToDisplay(rawAlertType: String): AlertTypeDisplay = when (rawAlertType) {
    "LUCES_ENCENDIDAS" -> AlertTypeDisplay("Luces encendidas", AlertTypeIconKind.LUCES_ENCENDIDAS)
    "PARKED_ON_GARAGE" -> AlertTypeDisplay("Estacionado sobre un garaje", AlertTypeIconKind.PARKED_ON_GARAGE)
    "PARKED_TOO_CLOSE" -> AlertTypeDisplay("Estacionado muy cerca de otro auto", AlertTypeIconKind.PARKED_TOO_CLOSE)
    "CRASHED" -> AlertTypeDisplay("Choque", AlertTypeIconKind.CRASHED)
    else -> AlertTypeDisplay("Incidente reportado", AlertTypeIconKind.GENERIC)
}
