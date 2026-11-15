package ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Sobre HPKE vigente (`{ "ephemeral_public_key", "ciphertext" }`, `info` vacío), el mismo del
 * signup y del signin. Es lo único que viaja por HTTP en los endpoints de US-13.
 */
@JsonClass(generateAdapter = true)
data class HpkeEnvelopeDto(
    @Json(name = "ephemeral_public_key") val ephemeralPublicKey: String,
    @Json(name = "ciphertext") val ciphertext: String,
)

/** Payloads internos: se serializan, se cifran y nunca salen de la app en claro. */
@JsonClass(generateAdapter = true)
data class EmailVerificationRequestPayloadDto(
    @Json(name = "email") val email: String,
) {
    override fun toString(): String = "EmailVerificationRequestPayloadDto(***)"
}

@JsonClass(generateAdapter = true)
data class EmailVerificationConfirmPayloadDto(
    @Json(name = "email") val email: String,
    @Json(name = "code") val code: String,
) {
    override fun toString(): String = "EmailVerificationConfirmPayloadDto(***)"
}

@JsonClass(generateAdapter = true)
data class PasswordResetRequestPayloadDto(
    @Json(name = "dni") val dni: String,
) {
    override fun toString(): String = "PasswordResetRequestPayloadDto(***)"
}

@JsonClass(generateAdapter = true)
data class PasswordResetConfirmPayloadDto(
    @Json(name = "dni") val dni: String,
    @Json(name = "code") val code: String,
) {
    override fun toString(): String = "PasswordResetConfirmPayloadDto(***)"
}

@JsonClass(generateAdapter = true)
data class PasswordResetCompletePayloadDto(
    @Json(name = "reset_token") val resetToken: String,
    @Json(name = "dni") val dni: String,
    @Json(name = "tramite") val procedureNumber: String,
    @Json(name = "sexo") val sex: String,
    /** JPEG redimensionado y recomprimido, en Base64. La única imagen que sale del dispositivo. */
    @Json(name = "selfie") val selfie: String,
    @Json(name = "new_password") val newPassword: String,
) {
    override fun toString(): String = "PasswordResetCompletePayloadDto(***)"
}

/** `200` de `email-verifications/confirm`. */
@JsonClass(generateAdapter = true)
data class VerificationTokenResponseDto(
    @Json(name = "verification_token") val verificationToken: String,
    @Json(name = "expires_in") val expiresIn: Long? = null,
) {
    override fun toString(): String = "VerificationTokenResponseDto(***)"
}

/** `200` de `password-resets/confirm`. */
@JsonClass(generateAdapter = true)
data class ResetTokenResponseDto(
    @Json(name = "reset_token") val resetToken: String,
    @Json(name = "expires_in") val expiresIn: Long? = null,
) {
    override fun toString(): String = "ResetTokenResponseDto(***)"
}

/**
 * Cuerpo de un error, sin cifrar. Los errores se mapean por [code], nunca por el status ni por
 * el texto de `details[].message`.
 */
@JsonClass(generateAdapter = true)
data class AuthErrorBodyDto(
    @Json(name = "code") val code: String? = null,
    @Json(name = "details") val details: List<AuthErrorDetailDto>? = null,
)

@JsonClass(generateAdapter = true)
data class AuthErrorDetailDto(
    @Json(name = "field") val field: String? = null,
    @Json(name = "code") val code: String? = null,
)
