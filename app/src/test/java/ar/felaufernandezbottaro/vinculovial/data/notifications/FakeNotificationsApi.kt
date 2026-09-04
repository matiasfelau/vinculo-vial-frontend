package ar.felaufernandezbottaro.vinculovial.data.notifications

import ar.felaufernandezbottaro.vinculovial.data.notifications.dto.FcmTokenRequestDto
import retrofit2.Response

class FakeNotificationsApi : NotificationsApi {

    var registerResponse: Response<Unit> = Response.success(Unit)
    var registerExceptionToThrow: Throwable? = null
    var lastRegisterAuthorization: String? = null
    var lastRegisterToken: String? = null
    var registerCallCount = 0

    var deleteResponse: Response<Unit> = Response.success(Unit)
    var deleteExceptionToThrow: Throwable? = null
    var lastDeleteAuthorization: String? = null
    var lastDeleteToken: String? = null
    var deleteCallCount = 0

    override suspend fun registerToken(authorization: String, body: FcmTokenRequestDto): Response<Unit> {
        registerCallCount++
        lastRegisterAuthorization = authorization
        lastRegisterToken = body.fcmToken
        registerExceptionToThrow?.let { throw it }
        return registerResponse
    }

    override suspend fun deleteToken(authorization: String, body: FcmTokenRequestDto): Response<Unit> {
        deleteCallCount++
        lastDeleteAuthorization = authorization
        lastDeleteToken = body.fcmToken
        deleteExceptionToThrow?.let { throw it }
        return deleteResponse
    }
}
