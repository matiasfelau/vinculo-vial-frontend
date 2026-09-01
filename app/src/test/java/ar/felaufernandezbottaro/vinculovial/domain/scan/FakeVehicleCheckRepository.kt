package ar.felaufernandezbottaro.vinculovial.domain.scan

/** Repositorio falso: registra las patentes recibidas y devuelve lo programado. */
class FakeVehicleCheckRepository(
    var result: CheckVehicleResult = CheckVehicleResult.Found(patEnc = "pat-enc-de-prueba"),
) : VehicleCheckRepository {

    val received = mutableListOf<String>()

    override suspend fun checkVehicle(patente: String): CheckVehicleResult {
        received += patente
        return result
    }
}
