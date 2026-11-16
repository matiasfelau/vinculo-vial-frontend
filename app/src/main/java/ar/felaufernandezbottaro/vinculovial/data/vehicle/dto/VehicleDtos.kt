package ar.felaufernandezbottaro.vinculovial.data.vehicle.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Payload interno de vincular y desvincular (US-14): la patente que el usuario escribió. Se
 * serializa y cifra junto -- nunca sale de la app en claro. Sin chasis: Vehicle lo ignoraría.
 *
 * [patenteBase64] viaja en base64 (no texto plano): el DTO del backend lo tipa `[]byte` para
 * poder purgarlo de memoria explícitamente (Zero Retention), y `encoding/json` de Go exige que un
 * campo `[]byte` venga codificado en base64.
 */
@JsonClass(generateAdapter = true)
data class VehiclePayloadDto(
    @Json(name = "patente") val patenteBase64: String,
) {
    override fun toString(): String = "VehiclePayloadDto(***)"
}

/**
 * Payload externo: un único criptograma HPKE (`enc` + ciphertext concatenados vía
 * `HpkeMessage.toCryptogram()`) cifrado contra la Public Key de Vehicle.
 */
@JsonClass(generateAdapter = true)
data class VehicleRequestDto(
    @Json(name = "payload_enc") val payloadEnc: String,
) {
    override fun toString(): String = "VehicleRequestDto(***)"
}

/** `{ "count", "max" }`: lo que responden alta, baja y consulta. Nunca trae patentes. */
@JsonClass(generateAdapter = true)
data class VehicleCountDto(
    @Json(name = "count") val count: Int,
    @Json(name = "max") val max: Int,
)

/**
 * Cuerpo de un error, `{ "error", "code" }`. Los errores se mapean por [code], nunca por el
 * status ni por el texto de [error].
 */
@JsonClass(generateAdapter = true)
data class VehicleErrorBodyDto(
    @Json(name = "code") val code: String? = null,
    @Json(name = "error") val error: String? = null,
)
