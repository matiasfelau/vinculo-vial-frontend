package com.vinculovial.app.domain.signup.model

/**
 * Payload interno del contrato US-01: lo único que se cifra y viaja a Identity.
 *
 * El DNI es el que devolvió el OCR (ya contrastado con el que tipeó el usuario),
 * no el texto crudo del formulario.
 */
data class Credentials(
    val dni: String,
    val email: String,
    val password: String,
) {
    override fun toString(): String = "Credentials(dni=***, email=***, password=***)"
}
