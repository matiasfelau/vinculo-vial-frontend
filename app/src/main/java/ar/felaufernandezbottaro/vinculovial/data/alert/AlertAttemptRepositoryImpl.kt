package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.AlertAttemptRequestDto
import ar.felaufernandezbottaro.vinculovial.data.alert.dto.AlertAttemptResponseDto
import ar.felaufernandezbottaro.vinculovial.data.alert.dto.ApiErrorDto
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoConfigurationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.crypto.toCryptogram
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.remote.isTransientUpstreamFailure
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.di.VehicleHpkePublicKey
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
 * Cifra la patente contra la Public Key de **Vehicle** (nunca la de IA: esa es sólo para las
 * fotos) y abre el intento en `/alerts/attempts`. El `pat_enc` resultante viaja en
 * [StartAlertAttemptResult.Started] para que el envío de la alerta lo reuse sin volver a cifrar.
 */
@Singleton
class AlertAttemptRepositoryImpl @Inject constructor(
    private val api: AlertAttemptApi,
    private val encryptor: HpkeEncryptor,
    @VehicleHpkePublicKey private val vehiclePublicKey: String,
    private val tokenStore: SecureTokenStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    moshi: Moshi,
) : AlertAttemptRepository {

    private val errorAdapter = moshi.adapter(ApiErrorDto::class.java)

    override suspend fun startAttempt(patente: String): StartAlertAttemptResult = withContext(dispatcher) {
        val patEnc = try {
            encryptPatente(patente)
        } catch (e: CryptoConfigurationException) {
            return@withContext StartAlertAttemptResult.Unexpected
        } catch (e: CryptoOperationException) {
            return@withContext StartAlertAttemptResult.Unexpected
        }

        requestAttempt(patEnc)
    }

    override suspend fun renewAttempt(patEnc: String): StartAlertAttemptResult = withContext(dispatcher) {
        requestAttempt(patEnc)
    }

    /** La llamada 1 con un [patEnc] ya cifrado: la comparten el intento inicial y su renovación. */
    private suspend fun requestAttempt(patEnc: String): StartAlertAttemptResult {
        val accessToken = tokenStore.read()?.accessToken ?: return StartAlertAttemptResult.Unexpected

        return try {
            api.startAttempt(BEARER_PREFIX + accessToken, AlertAttemptRequestDto(patEnc)).toResult(patEnc)
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

    private fun Response<AlertAttemptResponseDto>.toResult(patEnc: String): StartAlertAttemptResult {
        val status = code()
        return when {
            isSuccessful -> body()?.toStarted(patEnc) ?: StartAlertAttemptResult.Unexpected
            status == HTTP_BAD_REQUEST -> StartAlertAttemptResult.InvalidPlate
            status == HTTP_FORBIDDEN && apiErrorCode(errorAdapter) == CODE_ISSUER_UNDER_REVIEW -> StartAlertAttemptResult.UnderReview
            status == HTTP_NOT_FOUND -> StartAlertAttemptResult.NotInNetwork
            status == HTTP_TOO_MANY_REQUESTS -> StartAlertAttemptResult.RateLimited
            status.isTransientUpstreamFailure() -> StartAlertAttemptResult.Unavailable
            else -> StartAlertAttemptResult.Unexpected
        }
    }

    private fun AlertAttemptResponseDto.toStarted(patEnc: String): StartAlertAttemptResult =
        try {
            StartAlertAttemptResult.Started(
                attemptId = attemptId,
                expiresAt = OffsetDateTime.parse(expiresAt).toInstant(),
                patEnc = patEnc,
            )
        } catch (e: DateTimeParseException) {
            StartAlertAttemptResult.Unexpected
        }

    /**
     * Sin envoltorio JSON: a diferencia del `payload_enc` de US-04 (varios campos), acá se
     * cifra directamente el texto de la patente.
     */
    private fun encryptPatente(patente: String): String {
        val plaintext = patente.toByteArray(Charsets.UTF_8)
        return try {
            encryptor.encrypt(plaintext, vehiclePublicKey).toCryptogram()
        } finally {
            plaintext.fill(0)
        }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val CODE_ISSUER_UNDER_REVIEW = "ISSUER_UNDER_REVIEW"
    }
}
