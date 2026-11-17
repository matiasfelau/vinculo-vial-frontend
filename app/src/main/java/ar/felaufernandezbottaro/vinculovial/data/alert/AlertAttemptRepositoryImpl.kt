package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.AlertAttemptRequestDto
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
 * Abre el intento en `/alerts/attempts` con el `pat_enc` en el body (US-16): la patente va
 * cifrada para Vehicle, nunca en claro, y esta clase no tiene cómo generarla. No resuelve dueños.
 * Un 404 ya no significa "no está en la red" (el servidor no lo emite acá): cae en
 * [StartAlertAttemptResult.Unexpected]. Un 409 `ALERT_DAILY_LIMIT` es [StartAlertAttemptResult.DailyLimit].
 */
@Singleton
class AlertAttemptRepositoryImpl @Inject constructor(
    private val api: AlertAttemptApi,
    private val tokenStore: SecureTokenStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    moshi: Moshi,
) : AlertAttemptRepository {

    private val errorAdapter = moshi.adapter(ApiErrorDto::class.java)

    override suspend fun startAttempt(patEnc: String): StartAlertAttemptResult = withContext(dispatcher) {
        val accessToken = tokenStore.read()?.accessToken ?: return@withContext StartAlertAttemptResult.Unexpected

        try {
            api.startAttempt(BEARER_PREFIX + accessToken, AlertAttemptRequestDto(patEnc)).toResult()
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
            status == HTTP_CONFLICT -> conflictResult()
            status == HTTP_TOO_MANY_REQUESTS -> StartAlertAttemptResult.RateLimited
            status.isTransientUpstreamFailure() -> StartAlertAttemptResult.Unavailable
            else -> StartAlertAttemptResult.Unexpected
        }
    }

    /** Un 409 sólo es el cupo diario si trae ese `code` y un `available_at` legible; si no, es inesperado. */
    private fun Response<AlertAttemptResponseDto>.conflictResult(): StartAlertAttemptResult {
        val error = apiError(errorAdapter)
        val availableAt = error?.availableAtInstant()
        return if (error?.code == CODE_ALERT_DAILY_LIMIT && availableAt != null) {
            StartAlertAttemptResult.DailyLimit(availableAt)
        } else {
            StartAlertAttemptResult.Unexpected
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
