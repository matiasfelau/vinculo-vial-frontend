package ar.felaufernandezbottaro.vinculovial.domain.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.VehicleCount

/**
 * Repositorio falso de vehículos: registra lo que recibe y devuelve lo programado. Mantiene un
 * conteo propio para que alta y baja se vean reflejadas, igual que en el servidor.
 */
class FakeVehicleRepository(
    var countResult: VehicleResult<VehicleCount> = VehicleResult.Success(VehicleCount(count = 0, max = 3)),
    /** Si no es `null`, alta y baja devuelven esto en lugar de actualizar el conteo. */
    var linkFailure: VehicleError? = null,
    var unlinkFailure: VehicleError? = null,
    var unlinkAllResult: VehicleResult<Unit> = VehicleResult.Success(Unit),
) : VehicleRepository {

    val linked = mutableListOf<String>()
    val unlinked = mutableListOf<String>()
    var countCalls = 0
    var unlinkAllCalls = 0

    private var current = 0

    override suspend fun getCount(): VehicleResult<VehicleCount> {
        countCalls++
        return countResult
    }

    override suspend fun link(plate: String): VehicleResult<VehicleCount> {
        linked += plate
        linkFailure?.let { return VehicleResult.Failed(it) }
        current++
        return VehicleResult.Success(VehicleCount(count = current, max = 3))
    }

    override suspend fun unlink(plate: String): VehicleResult<VehicleCount> {
        unlinked += plate
        unlinkFailure?.let { return VehicleResult.Failed(it) }
        current = (current - 1).coerceAtLeast(0)
        return VehicleResult.Success(VehicleCount(count = current, max = 3))
    }

    override suspend fun unlinkAll(): VehicleResult<Unit> {
        unlinkAllCalls++
        return unlinkAllResult
    }
}
