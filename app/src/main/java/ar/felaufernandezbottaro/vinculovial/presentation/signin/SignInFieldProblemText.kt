package ar.felaufernandezbottaro.vinculovial.presentation.signin

import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInFieldProblem

/** Traduce un problema de validación local al mensaje que ve el usuario. */
fun SignInFieldProblem.message(): String = when (this) {
    SignInFieldProblem.REQUIRED -> "Completá este campo."
    SignInFieldProblem.DNI_FORMAT -> "El DNI debe tener 7 u 8 dígitos."
}
