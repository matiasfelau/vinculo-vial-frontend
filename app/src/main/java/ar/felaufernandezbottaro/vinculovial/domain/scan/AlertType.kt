package ar.felaufernandezbottaro.vinculovial.domain.scan

/**
 * Catálogo cerrado de tipos de incidente: el `alert_type` nunca es texto libre del usuario.
 *
 * [label] y [wireValue] están deliberadamente separados: el backend de `vinculo-vial-alert`
 * bindea el JSON contra constantes estrictas en UPPER_SNAKE_CASE ([wireValue]), no contra las
 * frases descriptivas en español que ve el usuario en el chat ([label]). Mandar [label] en el
 * campo `alert_type` del contrato es lo que producía el 400 del backend.
 */
enum class AlertType(val label: String, val wireValue: String) {
    PARKED_ON_GARAGE("Estacionado sobre un garaje", "PARKED_ON_GARAGE"),
    PARKED_TOO_CLOSE("Estacionado muy cerca de otro auto", "PARKED_TOO_CLOSE"),
    CRASHED("Chocado", "CRASHED"),
}
