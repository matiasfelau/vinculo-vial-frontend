package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.ApiErrorDto
import ar.felaufernandezbottaro.vinculovial.data.alert.dto.SendAlertMetadataDto
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoConfigurationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceEncryptor
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.remote.isTransientUpstreamFailure
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidencePhotoSet
import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidencePlan
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertRepository
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertResult
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arma el multipart de `POST /alerts` y mapea la respuesta. Las fotos se cifran acá, en cada
 * envío, con el `attempt_id` que recibe [sendAlert]: así un reenvío con otro intento sale
 * re-cifrado. No hay `HpkeEncryptor` ni clave pública entre sus dependencias: el
 * [EvidenceEncryptor] es el único que conoce la clave de IA, y la patente no viaja en este
 * request (el servidor ya la tiene del intento).
 */
@Singleton
class AlertRepositoryImpl @Inject constructor(
    private val api: AlertApi,
    private val evidenceEncryptor: EvidenceEncryptor,
    private val tokenStore: SecureTokenStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    moshi: Moshi,
) : AlertRepository {

    private val metadataAdapter = moshi.adapter(SendAlertMetadataDto::class.java)
    private val errorAdapter = moshi.adapter(ApiErrorDto::class.java)

    override suspend fun sendAlert(
        attemptId: String,
        alertType: AlertType,
        evidence: EvidencePhotoSet,
    ): SendAlertResult = withContext(dispatcher) {
        val accessToken = tokenStore.read()?.accessToken
            ?: return@withContext SendAlertResult.Failed(SendAlertError.Unexpected)

        val parts = try {
            buildParts(attemptId, alertType, evidence)
        } catch (e: CryptoConfigurationException) {
            return@withContext SendAlertResult.Failed(SendAlertError.Unexpected)
        } catch (e: CryptoOperationException) {
            return@withContext SendAlertResult.Failed(SendAlertError.Unexpected)
        } ?: return@withContext SendAlertResult.Failed(SendAlertError.Unexpected)

        try {
            api.sendAlert(BEARER_PREFIX + accessToken, parts).toResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CircuitBreakerOpenException) {
            SendAlertResult.Failed(SendAlertError.Unavailable)
        } catch (e: IOException) {
            SendAlertResult.Failed(SendAlertError.Network)
        } catch (e: RuntimeException) {
            SendAlertResult.Failed(SendAlertError.Unexpected)
        }
    }

    /**
     * `metadata` + una parte por foto, con el nombre del rol en minúscula. `null` si las fotos
     * no son las que pide el tipo (faltan o sobran): es un error de programación, no del usuario.
     */
    private fun buildParts(
        attemptId: String,
        alertType: AlertType,
        evidence: EvidencePhotoSet,
    ): List<MultipartBody.Part>? {
        val steps = try {
            EvidencePlan.stepsFor(alertType)
        } catch (e: IllegalArgumentException) {
            return null
        }

        // Como bytes, no como String: `String.toRequestBody` le agrega `; charset=utf-8` al
        // content-type, y el contrato pide exactamente `application/json`.
        val metadata = metadataAdapter.toJson(SendAlertMetadataDto(attemptId, alertType.wireValue))
            .toByteArray(Charsets.UTF_8)
        val parts = mutableListOf(
            MultipartBody.Part.createFormData(PART_METADATA, null, metadata.toRequestBody(JSON_MEDIA_TYPE)),
        )

        for (step in steps) {
            val partName = step.role.name.lowercase()
            val count = evidence.count(step.role)
            if (count !in 1..step.maxPhotos) return null

            for (index in 0 until count) {
                val ciphertext = evidence.read(step.role, index) { jpeg -> evidenceEncryptor.encrypt(jpeg, attemptId) }
                    ?: return null
                parts += MultipartBody.Part.createFormData(
                    partName,
                    "$partName-$index$PART_FILE_EXTENSION",
                    ciphertext.toRequestBody(OCTET_STREAM_MEDIA_TYPE),
                )
            }
        }
        return parts
    }

    private fun Response<Unit>.toResult(): SendAlertResult {
        if (isSuccessful) return SendAlertResult.Sent

        val status = code()
        val error = when {
            status == HTTP_BAD_REQUEST -> badRequestError(apiErrorCode(errorAdapter))
            status == HTTP_FORBIDDEN && apiErrorCode(errorAdapter) == CODE_ISSUER_UNDER_REVIEW -> SendAlertError.UnderReview
            status == HTTP_NOT_FOUND && apiErrorCode(errorAdapter) == CODE_ATTEMPT_NOT_FOUND -> SendAlertError.AttemptNotFound
            status == HTTP_GONE && apiErrorCode(errorAdapter) == CODE_ATTEMPT_EXPIRED -> SendAlertError.AttemptExpired
            status == HTTP_PAYLOAD_TOO_LARGE -> SendAlertError.EvidenceTooLarge
            status == HTTP_TOO_MANY_REQUESTS -> SendAlertError.RateLimited
            status.isTransientUpstreamFailure() -> SendAlertError.Unavailable
            else -> SendAlertError.Unexpected
        }
        return SendAlertResult.Failed(error)
    }

    private fun badRequestError(code: String?): SendAlertError = when (code) {
        CODE_INVALID_EVIDENCE -> SendAlertError.InvalidEvidence
        CODE_ALERT_TYPE_DISABLED -> SendAlertError.AlertTypeDisabled
        CODE_INVALID_ALERT_TYPE -> SendAlertError.InvalidAlertType
        else -> SendAlertError.InvalidData
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val PART_METADATA = "metadata"
        const val PART_FILE_EXTENSION = ".bin"

        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()

        const val HTTP_BAD_REQUEST = 400
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_GONE = 410
        const val HTTP_PAYLOAD_TOO_LARGE = 413
        const val HTTP_TOO_MANY_REQUESTS = 429

        const val CODE_ISSUER_UNDER_REVIEW = "ISSUER_UNDER_REVIEW"
        const val CODE_ATTEMPT_NOT_FOUND = "ATTEMPT_NOT_FOUND"
        const val CODE_ATTEMPT_EXPIRED = "ATTEMPT_EXPIRED"
        const val CODE_INVALID_EVIDENCE = "INVALID_EVIDENCE"
        const val CODE_ALERT_TYPE_DISABLED = "ALERT_TYPE_DISABLED"
        const val CODE_INVALID_ALERT_TYPE = "INVALID_ALERT_TYPE"
    }
}
