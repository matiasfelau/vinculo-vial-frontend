package ar.felaufernandezbottaro.vinculovial.domain.signup.model

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
 * Requisitos individuales de la contraseña. Separados de [CredentialProblem] a propósito: ese
 * enum devuelve un único motivo de rechazo (el primero que encuentra), pero la pantalla
 * necesita mostrar TODOS los requisitos incumplidos a la vez, no corregir al usuario de a uno.
 */
enum class PasswordRequirement { MIN_LENGTH, UPPERCASE, LOWERCASE, DIGIT, SYMBOL }

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

    /**
     * Forma canónica para comparar dos números de documento.
     *
     * El PDF417 a veces trae el número con ceros a la izquierda y el usuario casi nunca
     * los tipea: sin normalizar, "01234567" y "1234567" se verían como personas distintas.
     */
    fun normalizeDni(raw: String): String = raw.trim().trimStart('0')

    /**
     * Todos los requisitos que la contraseña todavía no cumple, no sólo el primero: así la UI
     * puede explicarlos de una sola vez en vez de corregir al usuario a cuentagotas.
     */
    fun unmetPasswordRequirements(password: String): Set<PasswordRequirement> = buildSet {
        if (password.length < MIN_PASSWORD_LENGTH) add(PasswordRequirement.MIN_LENGTH)
        if (password.none { it.isUpperCase() }) add(PasswordRequirement.UPPERCASE)
        if (password.none { it.isLowerCase() }) add(PasswordRequirement.LOWERCASE)
        if (password.none { it.isDigit() }) add(PasswordRequirement.DIGIT)
        if (password.none { !it.isLetterOrDigit() }) add(PasswordRequirement.SYMBOL)
    }

    private fun isStrongEnough(password: String): Boolean =
        password.any { it.isUpperCase() } &&
            password.any { it.isLowerCase() } &&
            password.any { it.isDigit() } &&
            password.any { !it.isLetterOrDigit() }
}
