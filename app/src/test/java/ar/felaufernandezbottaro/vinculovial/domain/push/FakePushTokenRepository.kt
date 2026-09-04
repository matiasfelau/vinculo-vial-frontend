package ar.felaufernandezbottaro.vinculovial.domain.push

class FakePushTokenRepository : PushTokenRepository {

    var registerResult: SyncFcmTokenResult = SyncFcmTokenResult.Success
    var deleteResult: DeleteFcmTokenResult = DeleteFcmTokenResult.Success

    val registeredTokens = mutableListOf<String>()
    val deletedTokens = mutableListOf<String>()

    override suspend fun registerToken(fcmToken: String): SyncFcmTokenResult {
        registeredTokens.add(fcmToken)
        return registerResult
    }

    override suspend fun deleteToken(fcmToken: String): DeleteFcmTokenResult {
        deletedTokens.add(fcmToken)
        return deleteResult
    }
}
