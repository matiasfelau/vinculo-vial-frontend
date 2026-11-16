package ar.felaufernandezbottaro.vinculovial.presentation.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.VehicleCount

/**
 * Estado único de "Mis vehículos" (US-14). Sólo sabe cuántos vehículos hay vinculados: ninguna
 * respuesta de Vehicle trae patentes, y la única patente que pasa por acá es la que el usuario
 * está escribiendo en un modal (ni se loguea ni se persiste).
 */
data class VehiclesUiState(
    val count: VehicleCount? = null,
    val loading: Boolean = true,
    val loadError: VehicleError? = null,
    val dialog: VehicleDialog = VehicleDialog.None,
    /** Hay una operación de red en vuelo (vincular, desvincular o desvincular todos). */
    val busy: Boolean = false,
) {
    /** Se puede vincular otro: ya se sabe el conteo, no llegó al máximo y no hay nada en vuelo. */
    val canLink: Boolean get() = count?.isFull == false && !busy

    override fun toString(): String = "VehiclesUiState(***)"
}

/** Qué se hace con la patente que el usuario escribe. */
enum class PlateAction { LINK, UNLINK }

/** El modal de patente tiene dos pasos: escribirla y confirmarla. */
enum class PlateStep { ENTRY, CONFIRM }

/** Por qué la patente escrita no se acepta en la app, antes de salir a la red. */
enum class PlateProblem { REQUIRED, FORMAT }

sealed interface VehicleDialog {

    data object None : VehicleDialog

    /** Modal de alta o de baja por patente. [error] es el rechazo del servidor, si lo hubo. */
    data class Plate(
        val action: PlateAction,
        val input: String = "",
        val step: PlateStep = PlateStep.ENTRY,
        val problem: PlateProblem? = null,
        val error: VehicleError? = null,
    ) : VehicleDialog {
        /** Nunca renderizar la patente que el usuario está escribiendo. */
        override fun toString(): String = "Plate(action=$action, step=$step)"
    }

    /** Confirmación de "Desvincular todos". */
    data class UnlinkAll(val error: VehicleError? = null) : VehicleDialog
}
