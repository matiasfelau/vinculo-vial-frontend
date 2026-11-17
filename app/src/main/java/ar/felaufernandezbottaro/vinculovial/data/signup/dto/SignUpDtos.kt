package ar.felaufernandezbottaro.vinculovial.data.signup.dto

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
    /**
     * US-13: lo emitió `email-verifications/confirm` para este mismo email. Identity lo valida
     * antes de RENAPER y lo consume al crear la cuenta.
     */
    @Json(name = "email_verification_token") val emailVerificationToken: String,
    /**
     * US-19: la versión de los Términos y Condiciones que el usuario aceptó (`TERMS_VERSION`).
     * Identity la compara con `TERMS_CURRENT_VERSION` antes de llamar a RENAPER y guarda la
     * versión y la fecha en el alta, como prueba del consentimiento.
     */
    @Json(name = "terms_version") val termsVersion: String,
) {
    override fun toString(): String = "SignUpPayloadDto(***)"
}

/**
 * Cuerpo de un 400, **sin cifrar** — a diferencia del payload de arriba, este viaja en
 * claro, es el propio Gateway/Identity describiendo por qué rechazó la request.
 *
 * [code] identifica la categoría: `"datos_invalidos"` trae [details] con el desglose por
 * campo; `"identidad_no_verificada"` (RENAPER no confirmó la selfie) no trae `details` —
 * es deliberadamente genérico, sin campo que señalar.
 */
@JsonClass(generateAdapter = true)
data class SignUpErrorResponseDto(
    @Json(name = "code") val code: String,
    @Json(name = "details") val details: List<SignUpErrorDetailDto>? = null,
)

/**
 * Un elemento de `details[]`. [code] es el valor estable para matchear en código —
 * `requerido` / `formato_invalido` / `longitud_invalida` / `password_debil` /
 * `password_predecible` hoy. [message] es texto para mostrar, pensado para cambiar de
 * redacción sin previo aviso: **nunca** matchear contra él.
 */
@JsonClass(generateAdapter = true)
data class SignUpErrorDetailDto(
    @Json(name = "field") val field: String,
    @Json(name = "code") val code: String,
    @Json(name = "message") val message: String? = null,
)
