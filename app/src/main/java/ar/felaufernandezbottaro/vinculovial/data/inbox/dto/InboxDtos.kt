package ar.felaufernandezbottaro.vinculovial.data.inbox.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Fila Zero-PII del buzón: `id`, `alert_type`, `created_at` y lo que el dueño necesita para ver
 * la evidencia redactada, valorar la validación y reportar al emisor. Los campos de US-08 tienen
 * default para tolerar un servidor que todavía no los manda.
 */
@JsonClass(generateAdapter = true)
data class AlertItemDto(
    val id: String,
    @Json(name = "alert_type") val alertType: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "requires_review") val requiresReview: Boolean = false,
    val evidence: List<EvidenceRefDto>? = null,
    @Json(name = "evidence_expires_at") val evidenceExpiresAt: String? = null,
    @Json(name = "my_feedback") val myFeedback: String? = null,
    val reported: Boolean = false,
)

/** `{ "role": "GENERAL", "index": 0 }`: una foto redactada que se puede bajar. */
@JsonClass(generateAdapter = true)
data class EvidenceRefDto(
    val role: String,
    val index: Int,
)

/** Página de `GET /api/v1/alerts/inbox`: un objeto `{items, next_cursor}`, nunca un array plano. */
@JsonClass(generateAdapter = true)
data class InboxPageDto(
    val items: List<AlertItemDto>,
    @Json(name = "next_cursor") val nextCursor: String?,
)

/** Cuerpo de `PUT /api/v1/alerts/{alert_id}/feedback`: `"UP"` o `"DOWN"`. */
@JsonClass(generateAdapter = true)
data class FeedbackRequestDto(
    val rating: String,
)

/** Cuerpo de `POST /api/v1/alerts/{alert_id}/report`: `"FALSE_ALERT"` o `"SPAM"`. */
@JsonClass(generateAdapter = true)
data class ReportRequestDto(
    val reason: String,
)
