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

/** Payload de `POST /api/v1/alerts`: reusa el `pat_enc` del check, nunca lo vuelve a cifrar. */
@JsonClass(generateAdapter = true)
data class SendAlertRequestDto(
    @Json(name = "alert_type") val alertType: String,
    @Json(name = "pat_enc") val patEnc: String,
) {
    override fun toString(): String = "SendAlertRequestDto(alertType=$alertType, patEnc=***)"
}
