package ar.felaufernandezbottaro.vinculovial.presentation.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.VehicleCount

/** Textos de "Mis vehículos" (US-14). En español rioplatense, con voseo. */
object VehicleTexts {
    const val TITLE = "Mis vehículos"
    const val LINK_BUTTON = "Vincular vehículo"
    const val UNLINK_BUTTON = "Desvincular un vehículo"
    const val UNLINK_ALL_BUTTON = "Desvincular todos"

    const val PLATE_LABEL = "PATENTE"
    const val PLATE_PLACEHOLDER = "AB123CD"
    const val CONTINUE = "Continuar"
    const val CONFIRM = "Confirmar"
    const val BACK = "Volver"
    const val CANCEL = "Cancelar"
    const val RETRY = "Reintentar"

    const val LINK_TITLE = "Vincular vehículo"
    const val UNLINK_TITLE = "Desvincular un vehículo"
    const val UNLINK_ALL_TITLE = "Desvincular todos"

    /** La salida de quien no recuerda cómo escribió una patente. */
    const val UNLINK_ALL_CONFIRMATION = "Vas a dejar de recibir las alertas de todos tus vehículos"

    const val PLATE_REQUIRED = "Ingresá la patente."
    const val PLATE_FORMAT = "La patente tiene que ser AAA999 o AA999AA."

    /** Por privacidad la app no dice cuáles: sólo cuántos. */
    fun summary(count: VehicleCount): String =
        "Tenés ${count.count} de ${count.max} vehículos vinculados"

    /** Mismo texto en el botón deshabilitado y en el error `VEHICLE_LIMIT_REACHED`. */
    fun limit(max: Int): String = "Podés tener hasta $max vehículos"

    fun linkConfirmation(plate: String): String =
        "Vas a vincular $plate. Vas a recibir las alertas de esta patente"

    fun unlinkConfirmation(plate: String): String =
        "Vas a desvincular $plate. Vas a dejar de recibir las alertas de esta patente"
}

fun PlateProblem.message(): String = when (this) {
    PlateProblem.REQUIRED -> VehicleTexts.PLATE_REQUIRED
    PlateProblem.FORMAT -> VehicleTexts.PLATE_FORMAT
}

/** Por el `code` del backend. [max] es el tope vigente, para el texto de `VEHICLE_LIMIT_REACHED`. */
fun VehicleError.message(max: Int): String = when (this) {
    VehicleError.Duplicate -> "Ya tenés esta patente vinculada"
    VehicleError.LimitReached -> VehicleTexts.limit(max)
    VehicleError.InvalidPlate -> "Revisá el formato de la patente"
    VehicleError.NotFound -> "No tenés esa patente vinculada"
    VehicleError.Unavailable ->
        "El servidor no está disponible en este momento. Intentá de nuevo en unos minutos."
    VehicleError.Network ->
        "No pudimos comunicarnos con el servidor. Revisá tu conexión e intentá de nuevo."
    VehicleError.Configuration -> "La app no está configurada. Probá más tarde o escribinos."
    VehicleError.Unexpected -> "Algo salió mal. Intentá de nuevo en unos minutos."
}
