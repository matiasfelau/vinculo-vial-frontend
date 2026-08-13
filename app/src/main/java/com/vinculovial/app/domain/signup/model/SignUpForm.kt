package com.vinculovial.app.domain.signup.model

/**
 * Datos que el ciudadano tipea en el formulario de registro.
 *
 * Vive en `domain` para que la validación sea idéntica en la UI (feedback inline)
 * y en el use case (última barrera antes de cifrar).
 */
data class SignUpForm(
    val dni: String = "",
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
) {
    /** Nunca exponer credenciales en logs ni en reportes de crash. */
    override fun toString(): String = "SignUpForm(dni=***, email=***, password=***)"
}

enum class CredentialField { DNI, EMAIL, PASSWORD, PASSWORD_CONFIRMATION }

enum class CredentialProblem {
    REQUIRED,
    DNI_FORMAT,
    EMAIL_FORMAT,
    PASSWORD_TOO_SHORT,
    PASSWORD_TOO_WEAK,
    PASSWORD_MISMATCH,
}

/**
 * Reglas de credenciales de US-01. El backend vuelve a validar: esto es UX, no seguridad.
 */
object CredentialRules {

    const val MIN_PASSWORD_LENGTH = 8

    private val DNI_REGEX = Regex("^\\d{7,8}$")
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    /** `null` significa que el campo es válido. */
    fun problemOf(field: CredentialField, form: SignUpForm): CredentialProblem? = when (field) {
        CredentialField.DNI -> when {
            form.dni.isBlank() -> CredentialProblem.REQUIRED
            !DNI_REGEX.matches(form.dni) -> CredentialProblem.DNI_FORMAT
            else -> null
        }

        CredentialField.EMAIL -> when {
            form.email.isBlank() -> CredentialProblem.REQUIRED
            !EMAIL_REGEX.matches(form.email.trim()) -> CredentialProblem.EMAIL_FORMAT
            else -> null
        }

        CredentialField.PASSWORD -> when {
            form.password.isBlank() -> CredentialProblem.REQUIRED
            form.password.length < MIN_PASSWORD_LENGTH -> CredentialProblem.PASSWORD_TOO_SHORT
            !isStrongEnough(form.password) -> CredentialProblem.PASSWORD_TOO_WEAK
            else -> null
        }

        CredentialField.PASSWORD_CONFIRMATION -> when {
            form.passwordConfirmation.isBlank() -> CredentialProblem.REQUIRED
            form.passwordConfirmation != form.password -> CredentialProblem.PASSWORD_MISMATCH
            else -> null
        }
    }

    /** Mapa vacío = formulario válido. */
    fun problems(form: SignUpForm): Map<CredentialField, CredentialProblem> =
        CredentialField.entries.mapNotNull { field ->
            problemOf(field, form)?.let { field to it }
        }.toMap()

    private fun isStrongEnough(password: String): Boolean =
        password.any { it.isUpperCase() } &&
            password.any { it.isLowerCase() } &&
            password.any { it.isDigit() }
}
