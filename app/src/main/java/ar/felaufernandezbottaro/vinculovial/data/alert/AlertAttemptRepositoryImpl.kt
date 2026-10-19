package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.AlertAttemptResponseDto
import ar.felaufernandezbottaro.vinculovial.data.alert.dto.ApiErrorDto
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.remote.isTransientUpstreamFailure
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertAttemptRepository
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptResult
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abre el intento en `/alerts/attempts`, sin body: no lleva la patente ni resuelve dueños. Un 404
 * ya no significa "no está en la red" (el servidor no lo emite acá): cae en [StartAlertAttemptResult.Unexpected].
 */
@Singleton
class AlertAttemptRepositoryImpl @Inject constructor(
    private val api: AlertAttemptApi,
    private val tokenStore: SecureTokenStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    moshi: Moshi,
) : AlertAttemptRepository {

    private val errorAdapter = moshi.adapter(ApiErrorDto::class.java)

    override suspend fun startAttempt(): StartAlertAttemptResult = withContext(dispatcher) {
        val accessToken = tokenStore.read()?.accessToken ?: return@withContext StartAlertAttemptResult.Unexpected

        try {
            api.startAttempt(BEARER_PREFIX + accessToken).toResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CircuitBreakerOpenException) {
            StartAlertAttemptResult.Unavailable
        } catch (e: IOException) {
            StartAlertAttemptResult.Unavailable
        } catch (e: RuntimeException) {
            StartAlertAttemptResult.Unexpected
        }
    }

    private fun Response<AlertAttemptResponseDto>.toResult(): StartAlertAttemptResult {
        val status = code()
        return when {
            isSuccessful -> body()?.toStarted() ?: StartAlertAttemptResult.Unexpected
            status == HTTP_FORBIDDEN && apiErrorCode(errorAdapter) == CODE_ISSUER_UNDER_REVIEW -> StartAlertAttemptResult.UnderReview
            status == HTTP_TOO_MANY_REQUESTS -> StartAlertAttemptResult.RateLimited
            status.isTransientUpstreamFailure() -> StartAlertAttemptResult.Unavailable
            else -> StartAlertAttemptResult.Unexpected
        }
    }

    private fun AlertAttemptResponseDto.toStarted(): StartAlertAttemptResult =
        try {
            StartAlertAttemptResult.Started(
                attemptId = attemptId,
                expiresAt = OffsetDateTime.parse(expiresAt).toInstant(),
            )
        } catch (e: DateTimeParseException) {
            StartAlertAttemptResult.Unexpected
        }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val CODE_ISSUER_UNDER_REVIEW = "ISSUER_UNDER_REVIEW"
    }
}
