package ar.felaufernandezbottaro.vinculovial.domain.signin.model

/**
 * Datos que el ciudadano tipea en el formulario de login.
 *
 * Vive en `domain` para que la validación sea idéntica en la UI (feedback inline) y en el
 * use case (última barrera antes de cifrar).
 */
data class SignInForm(
    val dni: String = "",
    val password: String = "",
) {
    /** Nunca exponer credenciales en logs ni en reportes de crash. */
    override fun toString(): String = "SignInForm(dni=***, password=***)"
}

enum class SignInField { DNI, PASSWORD }

enum class SignInFieldProblem { REQUIRED, DNI_FORMAT }

/**
 * Fail-Fast de US-02: sólo detecta lo obviamente roto (DNI con letras, contraseña vacía).
 * El backend vuelve a validar todo — esto es conveniencia de UX, no seguridad, y no intenta
 * repetir las reglas de fuerza de contraseña de `CredentialRules` (US-01): acá no se está
 * creando una contraseña, se está probando una que ya existe.
 */
object SignInRules {

    private val DNI_REGEX = Regex("^\\d{7,8}$")

    /** `null` significa que el campo es válido. */
    fun problemOf(field: SignInField, form: SignInForm): SignInFieldProblem? = when (field) {
        SignInField.DNI -> when {
            form.dni.isBlank() -> SignInFieldProblem.REQUIRED
            !DNI_REGEX.matches(form.dni.trim()) -> SignInFieldProblem.DNI_FORMAT
            else -> null
        }

        SignInField.PASSWORD -> when {
            form.password.isBlank() -> SignInFieldProblem.REQUIRED
            else -> null
        }
    }

    /** Mapa vacío = formulario válido. */
    fun problems(form: SignInForm): Map<SignInField, SignInFieldProblem> =
        SignInField.entries.mapNotNull { field ->
            problemOf(field, form)?.let { field to it }
        }.toMap()
}
