package ar.felaufernandezbottaro.vinculovial.data.vehicle.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Payload interno: los datos del vehículo leídos de la cédula. Se serializa y cifra junto
 * -- nunca sale de la app en claro. Sin DNI: el backend lo resuelve consultando una API
 * externa (RENAPER/DNRPA) a partir de estos datos.
 */
@JsonClass(generateAdapter = true)
data class VehiclePayloadDto(
    @Json(name = "chasis") val chasis: String,
    @Json(name = "patente") val patente: String,
    @Json(name = "nro_control") val nroControl: String,
) {
    override fun toString(): String = "VehiclePayloadDto(***)"
}

/**
 * Payload externo del contrato US-04: un único criptograma HPKE (`enc` + ciphertext
 * concatenados vía `HpkeMessage.toCryptogram()`) cifrado contra la Public Key de Vehicle.
 */
@JsonClass(generateAdapter = true)
data class LinkVehicleRequestDto(
    @Json(name = "payload_enc") val payloadEnc: String,
) {
    override fun toString(): String = "LinkVehicleRequestDto(***)"
}
