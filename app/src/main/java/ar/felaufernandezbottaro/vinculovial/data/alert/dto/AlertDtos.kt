package ar.felaufernandezbottaro.vinculovial.data.alert.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** 201 de `POST /api/v1/alerts/attempts`: `expires_at` es RFC 3339. */
@JsonClass(generateAdapter = true)
data class AlertAttemptResponseDto(
    @Json(name = "attempt_id") val attemptId: String,
    @Json(name = "expires_at") val expiresAt: String,
)

/**
 * Body de `POST /api/v1/alerts/attempts`: la patente cifrada para Vehicle, el mismo criptograma
 * (`pat_enc`) que viaja en el envío. Es un secreto en tránsito: [toString] no lo imprime.
 */
@JsonClass(generateAdapter = true)
data class AlertAttemptRequestDto(
    @Json(name = "pat_enc") val patEnc: String,
) {
    override fun toString(): String = "AlertAttemptRequestDto(patEnc=***)"
}

/**
 * Cuerpo de error del contrato de US-08: `{ "error": "<mensaje>", "code": "<CODE>" }`. El 409
 * `ALERT_DAILY_LIMIT` (US-16) suma `available_at`, RFC 3339.
 */
@JsonClass(generateAdapter = true)
data class ApiErrorDto(
    @Json(name = "error") val error: String? = null,
    @Json(name = "code") val code: String? = null,
    @Json(name = "available_at") val availableAt: String? = null,
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
