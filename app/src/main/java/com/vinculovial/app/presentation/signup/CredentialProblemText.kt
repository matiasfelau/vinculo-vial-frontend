package com.vinculovial.app.presentation.signup

import com.vinculovial.app.domain.signup.model.CredentialProblem
import com.vinculovial.app.domain.signup.model.CredentialRules

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
