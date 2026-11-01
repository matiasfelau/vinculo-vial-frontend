package ar.felaufernandezbottaro.vinculovial.data.alert.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** 201 de `POST /api/v1/alerts/attempts`: `expires_at` es RFC 3339. */
@JsonClass(generateAdapter = true)
data class AlertAttemptResponseDto(
    @Json(name = "attempt_id") val attemptId: String,
    @Json(name = "expires_at") val expiresAt: String,
)

/** Cuerpo de error del contrato de US-08: `{ "error": "<mensaje>", "code": "<CODE>" }`. */
@JsonClass(generateAdapter = true)
data class ApiErrorDto(
    @Json(name = "error") val error: String? = null,
    @Json(name = "code") val code: String? = null,
)

/**
 * Parte `metadata` de `POST /api/v1/alerts`: el intento, el tipo y la patente cifrada para Vehicle
 * (`pat_enc`, obligatorio: sin él el backend responde 400).
 */
@JsonClass(generateAdapter = true)
data class SendAlertMetadataDto(
    @Json(name = "attempt_id") val attemptId: String,
    @Json(name = "alert_type") val alertType: String,
    @Json(name = "pat_enc") val patEnc: String,
) {
    override fun toString(): String = "SendAlertMetadataDto(attemptId=$attemptId, alertType=$alertType, patEnc=***)"
}
