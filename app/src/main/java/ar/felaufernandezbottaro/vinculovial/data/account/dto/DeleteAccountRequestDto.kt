package ar.felaufernandezbottaro.vinculovial.data.account.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Cuerpo de `DELETE /api/v1/users/me`: `{ "password": "<string>" }`. */
@JsonClass(generateAdapter = true)
data class DeleteAccountRequestDto(
    @Json(name = "password") val password: String,
) {
    /** Nunca exponer la contraseña en logs ni en reportes de crash. */
    override fun toString(): String = "DeleteAccountRequestDto(password=***)"
}
