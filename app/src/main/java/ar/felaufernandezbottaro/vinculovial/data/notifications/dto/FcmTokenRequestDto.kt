package ar.felaufernandezbottaro.vinculovial.data.notifications.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FcmTokenRequestDto(
    @Json(name = "fcm_token") val fcmToken: String,
)
