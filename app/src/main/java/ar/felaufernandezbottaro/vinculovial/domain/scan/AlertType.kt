package ar.felaufernandezbottaro.vinculovial.domain.scan

/**
 * Catálogo cerrado de tipos de incidente: el `alert_type` nunca es texto libre del usuario,
 * coincide con el enum que espera `vinculo-vial-alert`. [value] se usa tal cual tanto para
 * mostrarlo en el `AlertTypeBottomSheet` como para el campo `alert_type` del contrato.
 */
enum class AlertType(val value: String) {
    PARKED_ON_GARAGE("Estacionado sobre un garaje"),
    PARKED_TOO_CLOSE("Estacionado muy cerca de otro auto"),
    CRASHED("Chocado"),
}
