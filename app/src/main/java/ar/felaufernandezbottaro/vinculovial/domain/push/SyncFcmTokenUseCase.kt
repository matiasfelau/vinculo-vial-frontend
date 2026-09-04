package ar.felaufernandezbottaro.vinculovial.domain.push

import javax.inject.Inject

class SyncFcmTokenUseCase @Inject constructor(
    private val repository: PushTokenRepository,
) {
    suspend operator fun invoke(fcmToken: String): SyncFcmTokenResult = repository.registerToken(fcmToken)
}
