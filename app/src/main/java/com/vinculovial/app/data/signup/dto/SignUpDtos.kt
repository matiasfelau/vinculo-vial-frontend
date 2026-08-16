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
 * Payload interno: se serializa, se cifra y nunca sale de la app en claro (el cifrado en sí
 * es lo único que protege a [selfie] en tránsito — HPKE no distingue campos "más sensibles"
 * dentro del mismo JSON).
 *
 * Pivote RENAPER-Ready: [selfie] reemplaza al viejo `biometric_verified`. Antes el booleano
 * era el único rastro del onboarding biométrico que llegaba al backend porque la comparación
 * ocurría enteramente en el dispositivo; ahora la comparación la hace el backend contra
 * RENAPER, así que necesita la imagen para poder hacerla. Es la única imagen que sale del
 * dispositivo en todo el flujo — el DNI nunca lo hace, sólo estos campos ya parseados.
 */
@JsonClass(generateAdapter = true)
data class SignUpPayloadDto(
    @Json(name = "dni") val dni: String,
    @Json(name = "tramite") val procedureNumber: String,
    @Json(name = "sexo") val sex: String,
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String,
    /** JPEG redimensionado a 800x800 máx. y recomprimido, codificado en Base64. */
    @Json(name = "selfie") val selfie: String,
) {
    override fun toString(): String = "SignUpPayloadDto(***)"
}
