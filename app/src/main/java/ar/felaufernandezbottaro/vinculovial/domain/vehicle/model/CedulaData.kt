package ar.felaufernandezbottaro.vinculovial.domain.vehicle.model

/**
 * Datos del vehículo ya extraídos del QR de la cédula (DNRPA), listos para empaquetarse en
 * un único payload y cifrarse contra la Public Key de Vehicle.
 *
 * No incluye el DNI del titular: el backend lo resuelve consultando una API externa
 * (RENAPER/DNRPA) a partir de estos datos, así que la app nunca lo lee ni lo cifra para
 * este flujo.
 */
data class CedulaData(
    val chasis: String,
    val patente: String,
    val nroControl: String,
) {
    override fun toString(): String = "CedulaData(***)"
}
