package ar.felaufernandezbottaro.vinculovial.domain.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.PlateRules
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.VehicleCount
import javax.inject.Inject

/**
 * Vincula la patente que el usuario escribió. Una patente con formato inválido no sale del
 * dispositivo: el dominio no confía en que la pantalla ya la validó.
 */
class LinkVehicleUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(plate: String): VehicleResult<VehicleCount> {
        val normalized = PlateRules.clean(plate)
        if (!PlateRules.isValid(normalized)) return VehicleResult.Failed(VehicleError.InvalidPlate)
        return repository.link(normalized)
    }
}

/** Desvincula la patente que el usuario volvió a escribir, con la misma validación que el alta. */
class UnlinkVehicleUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(plate: String): VehicleResult<VehicleCount> {
        val normalized = PlateRules.clean(plate)
        if (!PlateRules.isValid(normalized)) return VehicleResult.Failed(VehicleError.InvalidPlate)
        return repository.unlink(normalized)
    }
}

/** Desvincula todos los vehículos: la salida de quien no recuerda cómo escribió una patente. */
class UnlinkAllVehiclesUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(): VehicleResult<Unit> = repository.unlinkAll()
}

/** Cuántos vehículos tiene vinculados el usuario. */
class GetVehicleCountUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(): VehicleResult<VehicleCount> = repository.getCount()
}
