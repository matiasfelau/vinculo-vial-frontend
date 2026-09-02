package ar.felaufernandezbottaro.vinculovial.data.inbox.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Fila Zero-PII del buzón: sólo `id`, `alert_type` y `created_at`, tal como los expone el contrato. */
@JsonClass(generateAdapter = true)
data class AlertItemDto(
    val id: String,
    @Json(name = "alert_type") val alertType: String,
    @Json(name = "created_at") val createdAt: String,
)

/** Página de `GET /api/v1/alerts/inbox`: un objeto `{items, next_cursor}`, nunca un array plano. */
@JsonClass(generateAdapter = true)
data class InboxPageDto(
    val items: List<AlertItemDto>,
    @Json(name = "next_cursor") val nextCursor: String?,
)
