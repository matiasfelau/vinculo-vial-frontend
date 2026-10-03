package ar.felaufernandezbottaro.vinculovial.presentation.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaData

/**
 * Estado único de la pantalla de vinculación (US-04). No hay un estado separado de
 * formulario/captura como en el registro: el escaneo y la publicación del vínculo viven en
 * la misma pantalla, sin paso intermedio.
 */
data class VehicleUiState(
    val status: VehicleStatus = VehicleStatus.Idle,
) {
    val isLinking: Boolean get() = status is VehicleStatus.Linking
}

sealed interface VehicleStatus {
    /** Esperando que la cámara escanee la cédula. */
    data object Idle : VehicleStatus

    /** Cédula ya escaneada: cifrando y publicando el vínculo. */
    data object Linking : VehicleStatus

    /** 201: el vehículo quedó vinculado a la cuenta. */
    data object Linked : VehicleStatus

    data class Failed(val error: LinkVehicleError, val cedula: CedulaData) : VehicleStatus
}
