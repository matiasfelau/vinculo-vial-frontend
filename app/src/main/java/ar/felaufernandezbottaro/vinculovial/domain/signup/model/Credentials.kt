package ar.felaufernandezbottaro.vinculovial.domain.signup.model

/**
 * Payload interno del contrato US-01: lo único que se cifra y viaja a Identity.
 *
 * El dni/sexo/trámite son los que se leyeron del código de barras del documento, no lo que
 * tipeó el usuario. `selfieBase64` es la excepción deliberada al Zero Retention: la
 * verificación de identidad ahora la hace el backend contra RENAPER, así que la selfie
 * (ya redimensionada y comprimida, nunca la original) tiene que llegar. El resto — DNI
 * completo, apellido, nombres — nunca sale del dispositivo.
 */
data class Credentials(
    val dni: String,
    val procedureNumber: String,
    val sex: String,
    val email: String,
    val password: String,
    val selfieBase64: String,
) {
    override fun toString(): String = "Credentials(***)"
}
