package ar.felaufernandezbottaro.vinculovial.domain.scan

import javax.inject.Inject

class CheckVehicleUseCase @Inject constructor(private val repository: VehicleCheckRepository) {
    suspend operator fun invoke(patente: String): CheckVehicleResult = repository.checkVehicle(patente)
}
