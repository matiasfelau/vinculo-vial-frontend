package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.SendAlertRequestDto
import retrofit2.Response

class FakeAlertApi : AlertApi {

    var response: Response<Unit> = Response.success(Unit)
    var exceptionToThrow: Throwable? = null

    var lastAuthorization: String? = null
        private set
    var lastRequest: SendAlertRequestDto? = null
        private set
    var callCount = 0
        private set

    override suspend fun sendAlert(authorization: String, body: SendAlertRequestDto): Response<Unit> {
        callCount++
        lastAuthorization = authorization
        lastRequest = body
        exceptionToThrow?.let { throw it }
        return response
    }
}
