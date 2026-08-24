package ar.felaufernandezbottaro.vinculovial.domain.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaData
import javax.inject.Inject

/**
 * Vincula el vehículo cuya cédula ya se escaneó y parseó. No hay validación de dominio
 * adicional acá: [CedulaData] sólo existe si `CedulaQrParser` ya aceptó el contenido del
 * QR, así que este caso de uso es un pasamanos hacia [VehicleRepository] -- mismo criterio
 * que `LogoutUseCase`.
 */
class LinkVehicleUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(cedula: CedulaData): LinkVehicleResult = repository.linkVehicle(cedula)
}
