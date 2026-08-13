package com.vinculovial.app.data.signup.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Payload externo del contrato US-01: lo único que viaja por HTTP.
 * Encoding estándar de un mensaje HPKE modo BASE.
 */
@JsonClass(generateAdapter = true)
data class SignUpRequestDto(
    @Json(name = "ephemeral_public_key") val ephemeralPublicKey: String,
    @Json(name = "ciphertext") val ciphertext: String,
)

/**
 * Payload interno: se serializa, se cifra y nunca sale de la app en claro.
 */
@JsonClass(generateAdapter = true)
data class SignUpPayloadDto(
    @Json(name = "dni") val dni: String,
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String,
) {
    override fun toString(): String = "SignUpPayloadDto(***)"
}
