package ar.felaufernandezbottaro.vinculovial.data.notifications

import ar.felaufernandezbottaro.vinculovial.data.notifications.dto.FcmTokenRequestDto
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.remote.isTransientUpstreamFailure
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.domain.push.DeleteFcmTokenError
import ar.felaufernandezbottaro.vinculovial.domain.push.DeleteFcmTokenResult
import ar.felaufernandezbottaro.vinculovial.domain.push.PushTokenRepository
import ar.felaufernandezbottaro.vinculovial.domain.push.SyncFcmTokenError
import ar.felaufernandezbottaro.vinculovial.domain.push.SyncFcmTokenResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `/notifications/tokens` es un endpoint autenticado por JWT, no por attestation -- mismo
 * criterio que `/alerts/inbox`. El alta y la baja comparten el mismo cliente autenticado, así
 * que un access token vencido dispara el refresh automático del `TokenAuthenticator` de OkHttp
 * antes de llegar acá.
 */
@Singleton
class PushTokenRepositoryImpl @Inject constructor(
    private val api: NotificationsApi,
    private val tokenStore: SecureTokenStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : PushTokenRepository {

    override suspend fun registerToken(fcmToken: String): SyncFcmTokenResult = withContext(dispatcher) {
        val accessToken = tokenStore.read()?.accessToken
            ?: return@withContext SyncFcmTokenResult.Failed(SyncFcmTokenError.Unexpected)

        try {
            val response = api.registerToken(BEARER_PREFIX + accessToken, FcmTokenRequestDto(fcmToken))

            when {
                response.isSuccessful -> SyncFcmTokenResult.Success
                response.code() == HTTP_TOO_MANY_REQUESTS -> SyncFcmTokenResult.Failed(SyncFcmTokenError.RateLimited)
                response.code().isTransientUpstreamFailure() -> SyncFcmTokenResult.Failed(SyncFcmTokenError.Unavailable)
                else -> SyncFcmTokenResult.Failed(SyncFcmTokenError.Unexpected)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CircuitBreakerOpenException) {
            SyncFcmTokenResult.Failed(SyncFcmTokenError.Unavailable)
        } catch (e: IOException) {
            SyncFcmTokenResult.Failed(SyncFcmTokenError.Network)
        } catch (e: RuntimeException) {
            SyncFcmTokenResult.Failed(SyncFcmTokenError.Unexpected)
        }
    }

    override suspend fun deleteToken(fcmToken: String): DeleteFcmTokenResult = withContext(dispatcher) {
        val accessToken = tokenStore.read()?.accessToken
            ?: return@withContext DeleteFcmTokenResult.Failed(DeleteFcmTokenError.Unexpected)

        try {
            val response = api.deleteToken(BEARER_PREFIX + accessToken, FcmTokenRequestDto(fcmToken))

            when {
                response.isSuccessful -> DeleteFcmTokenResult.Success
                response.code().isTransientUpstreamFailure() -> DeleteFcmTokenResult.Failed(DeleteFcmTokenError.Unavailable)
                else -> DeleteFcmTokenResult.Failed(DeleteFcmTokenError.Unexpected)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CircuitBreakerOpenException) {
            DeleteFcmTokenResult.Failed(DeleteFcmTokenError.Unavailable)
        } catch (e: IOException) {
            DeleteFcmTokenResult.Failed(DeleteFcmTokenError.Network)
        } catch (e: RuntimeException) {
            DeleteFcmTokenResult.Failed(DeleteFcmTokenError.Unexpected)
        }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
