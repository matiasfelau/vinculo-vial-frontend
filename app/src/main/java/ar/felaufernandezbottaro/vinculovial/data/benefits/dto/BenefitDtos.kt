package ar.felaufernandezbottaro.vinculovial.data.benefits.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Un elemento de `GET /api/v1/benefits`. `banner_url` llega pero no se usa: la app arma la ruta
 * del banner desde el [id], para que un servidor (o un intermediario) no pueda mandar el Bearer
 * a otro host.
 */
@JsonClass(generateAdapter = true)
data class BenefitDto(
    @Json(name = "id") val id: String,
    @Json(name = "partner_name") val partnerName: String,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "terms") val terms: String,
    @Json(name = "open_url") val openUrl: String,
    @Json(name = "ends_at") val endsAt: String,
    @Json(name = "code") val code: String? = null,
) {
    override fun toString(): String = "BenefitDto(id=$id, title=$title, code=${if (code == null) "null" else "***"})"
}

/** 200 de `POST /api/v1/benefits/{id}/claim`. El código es un secreto: no se imprime. */
@JsonClass(generateAdapter = true)
data class ClaimResponseDto(
    @Json(name = "code") val code: String,
) {
    override fun toString(): String = "ClaimResponseDto(code=***)"
}
