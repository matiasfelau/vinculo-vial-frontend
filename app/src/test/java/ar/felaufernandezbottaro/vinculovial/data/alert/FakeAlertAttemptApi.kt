package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.AlertAttemptRequestDto
import ar.felaufernandezbottaro.vinculovial.data.alert.dto.AlertAttemptResponseDto
import retrofit2.Response

class FakeAlertAttemptApi : AlertAttemptApi {

    var response: Response<AlertAttemptResponseDto> = Response.success(
        201,
        AlertAttemptResponseDto(attemptId = "attempt-de-prueba", expiresAt = "2030-01-01T00:10:00Z"),
    )
    var exceptionToThrow: Throwable? = null

    var lastAuthorization: String? = null
        private set
    var lastRequest: AlertAttemptRequestDto? = null
        private set
    var callCount = 0
        private set

    override suspend fun startAttempt(
        authorization: String,
        body: AlertAttemptRequestDto,
    ): Response<AlertAttemptResponseDto> {
        callCount++
        lastAuthorization = authorization
        lastRequest = body
        exceptionToThrow?.let { throw it }
        return response
    }
}
