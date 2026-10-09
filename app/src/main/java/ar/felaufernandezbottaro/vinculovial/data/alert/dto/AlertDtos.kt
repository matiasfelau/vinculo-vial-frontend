package ar.felaufernandezbottaro.vinculovial.data.alert.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Payload de `POST /api/v1/alerts/attempts`: el mismo `pat_enc` que después reusa `/alerts`. */
@JsonClass(generateAdapter = true)
data class AlertAttemptRequestDto(
    @Json(name = "pat_enc") val patEnc: String,
) {
    override fun toString(): String = "AlertAttemptRequestDto(***)"
}

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

/** Parte `metadata` de `POST /api/v1/alerts`: el intento y el tipo. La patente ya no viaja acá. */
@JsonClass(generateAdapter = true)
data class SendAlertMetadataDto(
    @Json(name = "attempt_id") val attemptId: String,
    @Json(name = "alert_type") val alertType: String,
)
