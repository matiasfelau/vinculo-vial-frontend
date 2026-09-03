package ar.felaufernandezbottaro.vinculovial.domain.push

class FakeFcmTokenProvider : FcmTokenProvider {
    var token: String? = "fcm-token-default"
    override suspend fun getCurrentToken(): String? = token
}
