package ar.felaufernandezbottaro.vinculovial.domain.push

class FakeFcmTokenProvider : FcmTokenProvider {
    var token: String? = "fcm-token-default"
    override suspend fun getCurrentToken(): String? = token

    var deleteSucceeds = true
    var deleteCallCount = 0
        private set

    override suspend fun deleteCurrentToken(): Boolean {
        deleteCallCount++
        return deleteSucceeds
    }
}
