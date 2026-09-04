package ar.felaufernandezbottaro.vinculovial.presentation.signup

import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialProblem
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialRules
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.PasswordRequirement

/** Traduce un problema de dominio al mensaje que ve el usuario. */
fun CredentialProblem.message(): String = when (this) {
    CredentialProblem.REQUIRED -> "Completá este campo."
    CredentialProblem.DNI_FORMAT -> "El DNI debe tener 7 u 8 dígitos."
    CredentialProblem.EMAIL_FORMAT -> "Ingresá un email válido."
    CredentialProblem.PASSWORD_TOO_SHORT ->
        "La contraseña debe tener al menos ${CredentialRules.MIN_PASSWORD_LENGTH} caracteres."
    CredentialProblem.PASSWORD_TOO_WEAK -> "Debe incluir mayúscula, minúscula y número."
    CredentialProblem.PASSWORD_MISMATCH -> "Las contraseñas no coinciden."
}

private fun PasswordRequirement.message(): String = when (this) {
    PasswordRequirement.MIN_LENGTH ->
        "Debe tener al menos ${CredentialRules.MIN_PASSWORD_LENGTH} caracteres."
    PasswordRequirement.UPPERCASE -> "Debe incluir una mayúscula."
    PasswordRequirement.LOWERCASE -> "Debe incluir una minúscula."
    PasswordRequirement.DIGIT -> "Debe incluir un número."
}

/**
 * Todos los requisitos incumplidos como un único texto, en vez de mostrar uno y corregir al
 * usuario recién en el próximo intento.
 */
fun Set<PasswordRequirement>.message(): String = joinToString(separator = " ") { it.message() }
