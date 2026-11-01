package ar.felaufernandezbottaro.vinculovial.domain.scan

/**
 * Catálogo cerrado de tipos de incidente: el `alert_type` nunca es texto libre del usuario.
 *
 * [label] y [wireValue] están deliberadamente separados: el backend de `vinculo-vial-alert`
 * bindea el JSON contra constantes estrictas en UPPER_SNAKE_CASE ([wireValue]), no contra las
 * frases descriptivas en español que ve el usuario en el chat ([label]). Mandar [label] en el
 * campo `alert_type` del contrato es lo que producía el 400 del backend.
 *
 * [isOffered] dice si la app deja elegir el tipo: sólo los que tienen validación de IA. Los
 * deshabilitados se conservan en el enum únicamente para mostrar alertas históricas.
 */
enum class AlertType(val label: String, val wireValue: String, val isOffered: Boolean = true) {
    PARKED_ON_GARAGE(
        label = "Estacionado frente a una rampa (garage o de accesibilidad)",
        wireValue = "PARKED_ON_GARAGE",
    ),
    PARKED_TOO_CLOSE(
        label = "Estacionado muy cerca de otro auto",
        wireValue = "PARKED_TOO_CLOSE",
        isOffered = false,
    ),
    CRASHED(label = "Chocado", wireValue = "CRASHED"),
    ;

    companion object {
        /** Los tipos que el selector del chat ofrece. */
        val offered: List<AlertType> = entries.filter { it.isOffered }
    }
}
