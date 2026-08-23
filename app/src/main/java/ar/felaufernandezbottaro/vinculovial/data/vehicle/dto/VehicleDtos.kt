package ar.felaufernandezbottaro.vinculovial.data.vehicle.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Payload interno: los datos del vehículo leídos de la cédula. Se serializa y cifra junto
 * -- nunca sale de la app en claro. Sin DNI: el backend lo resuelve consultando una API
 * externa (RENAPER/DNRPA) a partir de estos datos. Sin número de control: el backend sólo
 * necesita chasis y patente para vincular.
 *
 * [chasis] y [patente] viajan en base64 (no texto plano): el DTO del backend los tipa
 * `[]byte` para poder purgarlos de memoria explícitamente (Zero Retention), y `encoding/json`
 * de Go exige que un campo `[]byte` venga codificado en base64 -- un string plano ahí
 * produce un error de parseo del lado del servidor.
 */
@JsonClass(generateAdapter = true)
data class VehiclePayloadDto(
    @Json(name = "chasis") val chasisBase64: String,
    @Json(name = "patente") val patenteBase64: String,
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
