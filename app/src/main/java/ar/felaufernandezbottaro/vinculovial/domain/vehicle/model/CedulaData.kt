package ar.felaufernandezbottaro.vinculovial.domain.vehicle.model

/**
 * DNI y patente ya extraídos del QR de la cédula del vehículo, listos para cifrarse cada
 * uno contra su destinatario (ver el cifrado diferenciado de US-04 en `HpkeEncryptor`).
 */
data class CedulaData(
    val dni: String,
    val patente: String,
) {
    override fun toString(): String = "CedulaData(***)"
}
