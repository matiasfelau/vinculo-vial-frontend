package com.vinculovial.app.domain.signup.model

/**
 * Payload interno del contrato US-01: lo único que se cifra y viaja a Identity.
 *
 * El DNI es el que se leyó del PDF417 del documento, no el que tipeó el usuario.
 * Las imágenes nunca salen del dispositivo: de toda la verificación biométrica sólo
 * viaja [biometricVerified].
 */
data class Credentials(
    val dni: String,
    val email: String,
    val password: String,
    val biometricVerified: Boolean,
) {
    override fun toString(): String =
        "Credentials(dni=***, email=***, password=***, biometricVerified=$biometricVerified)"
}
