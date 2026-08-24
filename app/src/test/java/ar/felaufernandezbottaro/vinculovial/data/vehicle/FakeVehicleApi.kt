package ar.felaufernandezbottaro.vinculovial.data.vehicle

import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.LinkVehicleRequestDto
import retrofit2.Response

class FakeVehicleApi : VehicleApi {

    var response: Response<Unit> = Response.success(Unit)
    var exceptionToThrow: Throwable? = null

    var lastAuthorization: String? = null
        private set
    var lastRequest: LinkVehicleRequestDto? = null
        private set
    var callCount = 0
        private set

    override suspend fun linkVehicle(authorization: String, body: LinkVehicleRequestDto): Response<Unit> {
        callCount++
        lastAuthorization = authorization
        lastRequest = body
        exceptionToThrow?.let { throw it }
        return response
    }
}
