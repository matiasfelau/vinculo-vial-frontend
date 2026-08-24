package ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Payload externo compartido por `/refresh` y `/logout` (contrato US-03). A diferencia de
 * US-01/US-02 viaja en JSON plano, sin el wrapper HPKE: el refresh token es de un solo uso
 * y la confidencialidad en tránsito ya la aporta HTTPS.
 */
@JsonClass(generateAdapter = true)
data class RefreshTokenRequestDto(
    @Json(name = "refresh_token") val refreshToken: String,
) {
    override fun toString(): String = "RefreshTokenRequestDto(***)"
}

/** Cuerpo del 200 de `/refresh`: siempre un par nuevo, rotado respecto al que lo pidió. */
@JsonClass(generateAdapter = true)
data class RefreshResponseDto(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
) {
    override fun toString(): String = "RefreshResponseDto(***)"
}
