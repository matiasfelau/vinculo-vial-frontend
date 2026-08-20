package ar.felaufernandezbottaro.vinculovial.data.signin.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Payload externo del contrato US-02: lo único que viaja por HTTP.
 * Encoding estándar de un mensaje HPKE modo BASE — mismo wrapper que US-01.
 */
@JsonClass(generateAdapter = true)
data class SignInRequestDto(
    @Json(name = "ephemeral_public_key") val ephemeralPublicKey: String,
    @Json(name = "ciphertext") val ciphertext: String,
)

/** Payload interno: se serializa, se cifra y nunca sale de la app en claro. */
@JsonClass(generateAdapter = true)
data class SignInPayloadDto(
    @Json(name = "dni") val dni: String,
    @Json(name = "password") val password: String,
) {
    override fun toString(): String = "SignInPayloadDto(***)"
}

/**
 * Cuerpo del 200: a diferencia de `/signup`, acá sí hay algo que leer — el par de tokens
 * de sesión. Viaja en claro (no HPKE): HTTPS ya lo protege en tránsito, y son valores de
 * un solo uso por sesión, no PII.
 */
@JsonClass(generateAdapter = true)
data class SignInResponseDto(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
) {
    override fun toString(): String = "SignInResponseDto(***)"
}
