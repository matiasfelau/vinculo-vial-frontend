package ar.felaufernandezbottaro.vinculovial.domain.push

import javax.inject.Inject

class DeleteFcmTokenUseCase @Inject constructor(
    private val repository: PushTokenRepository,
) {
    suspend operator fun invoke(fcmToken: String): DeleteFcmTokenResult = repository.deleteToken(fcmToken)
}
