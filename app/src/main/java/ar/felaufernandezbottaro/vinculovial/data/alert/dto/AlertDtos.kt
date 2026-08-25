package ar.felaufernandezbottaro.vinculovial.data.alert.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Payload de `POST /api/v1/vehicles/check`: el mismo `pat_enc` que después reusa `/alerts`. */
@JsonClass(generateAdapter = true)
data class VehicleCheckRequestDto(
    @Json(name = "pat_enc") val patEnc: String,
) {
    override fun toString(): String = "VehicleCheckRequestDto(***)"
}
