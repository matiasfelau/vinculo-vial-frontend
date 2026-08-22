package ar.felaufernandezbottaro.vinculovial.domain.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaData

/**
 * Puerto de salida de la vinculación de vehículo. La implementación (cifrado diferenciado
 * HPKE + HTTP) vive en `data`.
 */
interface VehicleRepository {
    suspend fun linkVehicle(cedula: CedulaData): LinkVehicleResult
}
