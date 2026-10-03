package ar.felaufernandezbottaro.vinculovial.presentation.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleError

/**
 * Mensajes de US-04. [LinkVehicleError.OwnerMismatch] es deliberadamente cerrado: no
 * sugiere reintentar con otra cédula ni con otro DNI, y no da a entender que el DNI
 * escaneado existe en el sistema -- sólo que esta cuenta no puede vincularlo.
 */
fun LinkVehicleError.message(): String = when (this) {
    LinkVehicleError.OwnerMismatch ->
        "La cédula escaneada no corresponde a tu cuenta."

    LinkVehicleError.AlreadyLinked ->
        "Ya tenés este vehículo vinculado a tu cuenta."

    LinkVehicleError.InvalidData ->
        "No pudimos procesar la cédula escaneada. Volvé a intentarlo."

    LinkVehicleError.Configuration ->
        "La app no está configurada. Probá más tarde o escribinos."

    LinkVehicleError.Unavailable ->
        "El servidor no está disponible en este momento. Intentá de nuevo en unos minutos."

    LinkVehicleError.Network ->
        "No pudimos comunicarnos con el servidor. Revisá tu conexión e intentá de nuevo."

    LinkVehicleError.Unexpected ->
        "No pudimos vincular el vehículo. Intentá de nuevo en unos minutos."
}
