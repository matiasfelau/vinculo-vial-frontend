package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.VehicleCheckRequestDto
import retrofit2.Response

class FakeVehicleCheckApi : VehicleCheckApi {

    var response: Response<Unit> = Response.success(Unit)
    var exceptionToThrow: Throwable? = null

    var lastAuthorization: String? = null
        private set
    var lastRequest: VehicleCheckRequestDto? = null
        private set
    var callCount = 0
        private set

    override suspend fun checkVehicle(authorization: String, body: VehicleCheckRequestDto): Response<Unit> {
        callCount++
        lastAuthorization = authorization
        lastRequest = body
        exceptionToThrow?.let { throw it }
        return response
    }
}
