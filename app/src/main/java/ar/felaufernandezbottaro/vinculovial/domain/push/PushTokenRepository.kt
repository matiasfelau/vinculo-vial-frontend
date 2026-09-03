package ar.felaufernandezbottaro.vinculovial.domain.push

interface PushTokenRepository {
    suspend fun registerToken(fcmToken: String): SyncFcmTokenResult
    suspend fun deleteToken(fcmToken: String): DeleteFcmTokenResult
}
