package ar.felaufernandezbottaro.vinculovial.presentation.scan

import ar.felaufernandezbottaro.vinculovial.domain.scan.CheckVehicleError
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError

fun CheckVehicleError.message(): String = when (this) {
    CheckVehicleError.RateLimited ->
        "Alcanzaste el límite de consultas. Esperá unos minutos antes de volver a intentar."

    CheckVehicleError.Configuration ->
        "La app no está configurada. Probá más tarde o escribinos."

    CheckVehicleError.Network ->
        "No pudimos comunicarnos con el servidor. Revisá tu conexión e intentá de nuevo."

    CheckVehicleError.Unexpected ->
        "No pudimos verificar la patente. Intentá de nuevo en unos minutos."
}

fun SendAlertError.message(): String = when (this) {
    SendAlertError.InvalidData ->
        "No pudimos procesar el reporte. Volvé a intentarlo."

    SendAlertError.RateLimited ->
        "Alcanzaste el límite de reportes. Esperá unos minutos antes de volver a intentar."

    SendAlertError.Network ->
        "No pudimos comunicarnos con el servidor. Revisá tu conexión e intentá de nuevo."

    SendAlertError.Unexpected ->
        "No pudimos enviar el reporte. Intentá de nuevo en unos minutos."
}
