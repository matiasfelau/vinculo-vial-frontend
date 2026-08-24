package ar.felaufernandezbottaro.vinculovial.domain.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaData

/** Repositorio falso: registra las cédulas recibidas y devuelve lo programado. */
class FakeVehicleRepository(
    var result: LinkVehicleResult = LinkVehicleResult.Linked,
) : VehicleRepository {

    val received = mutableListOf<CedulaData>()

    override suspend fun linkVehicle(cedula: CedulaData): LinkVehicleResult {
        received += cedula
        return result
    }
}
