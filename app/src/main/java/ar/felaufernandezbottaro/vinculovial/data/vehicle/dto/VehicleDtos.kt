package ar.felaufernandezbottaro.vinculovial.data.vehicle.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Payload externo del contrato US-04: cada campo es un único criptograma HPKE (`enc` +
 * ciphertext concatenados vía `HpkeMessage.toCryptogram()`), no el wrapper de dos campos
 * que usan `/signup` y `/signin`.
 *
 * Cifrado diferenciado: [dniEnc] sólo lo puede abrir Identity, [patEnc] sólo Vehicle.
 * Vehicle reenvía [dniEnc] sin abrirlo hacia `/internal/verify-ownership`.
 */
@JsonClass(generateAdapter = true)
data class LinkVehicleRequestDto(
    @Json(name = "dni_enc") val dniEnc: String,
    @Json(name = "pat_enc") val patEnc: String,
) {
    override fun toString(): String = "LinkVehicleRequestDto(***)"
}
