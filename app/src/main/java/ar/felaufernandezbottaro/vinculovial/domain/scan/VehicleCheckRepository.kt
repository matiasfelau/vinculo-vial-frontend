package ar.felaufernandezbottaro.vinculovial.domain.scan

interface VehicleCheckRepository {

    /** Cifra [patente] contra la Public Key de Vehicle y consulta si está en la red. */
    suspend fun checkVehicle(patente: String): CheckVehicleResult
}
