package ar.felaufernandezbottaro.vinculovial.domain.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.VehicleCount

/**
 * Puerto de salida de los vehículos vinculados (US-14). La implementación (cifrado HPKE contra
 * Vehicle + HTTP) vive en `data`. Ninguna operación devuelve patentes: sólo el conteo.
 */
interface VehicleRepository {

    /** `GET /vehicles`: cuántos vehículos tiene vinculados el usuario. */
    suspend fun getCount(): VehicleResult<VehicleCount>

    /** `POST /vehicles`: vincula [plate] (ya normalizada y válida) al usuario de la sesión. */
    suspend fun link(plate: String): VehicleResult<VehicleCount>

    /** `POST /vehicles/unlink`: desvincula [plate], que el usuario tiene que volver a escribir. */
    suspend fun unlink(plate: String): VehicleResult<VehicleCount>

    /** `DELETE /vehicles`: desvincula todos los vehículos del usuario. */
    suspend fun unlinkAll(): VehicleResult<Unit>
}
