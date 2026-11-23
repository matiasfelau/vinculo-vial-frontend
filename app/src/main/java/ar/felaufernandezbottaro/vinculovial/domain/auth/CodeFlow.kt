package ar.felaufernandezbottaro.vinculovial.domain.auth

/**
 * Por qué no se pudo completar un pedido a los endpoints públicos de `/auth` que no es culpa
 * de los datos: red, disponibilidad o seguridad del dispositivo. Lo comparten la verificación
 * del email (US-13, alta) y la recuperación de contraseña (US-13), que hablan con Identity de
 * la misma manera.
 */
enum class RequestFailure {
    /** Sin conexión, timeout o servidor inalcanzable. */
    NETWORK,

    /** 502/503/504 o circuito abierto: transitorio, reintentable. */
    UNAVAILABLE,

    /** 429: el Gateway frenó por rate limit. */
    RATE_LIMITED,

    /** 403: el Gateway rechazó la App Attestation. */
    ATTESTATION_REJECTED,

    /** El dispositivo no pasó la verificación de integridad local. */
    DEVICE_NOT_TRUSTED,

    /** No se pudo obtener el veredicto de integridad; reintentar puede funcionar. */
    ATTESTATION_UNAVAILABLE,

    /** Falta o está mal la configuración de build (clave HPKE, PSK). */
    CONFIGURATION,

    /** Cualquier otra cosa. */
    UNEXPECTED,
}

/** Resultado de pedir un código por email: el 202 es idéntico exista o no la cuenta. */
sealed interface CodeRequestResult {

    /** 202: el pedido se aceptó. No dice si se envió algo (anti-enumeración). */
    data object Sent : CodeRequestResult

    /** 400 `datos_invalidos`: el email o el DNI tiene un formato inválido. */
    data object InvalidInput : CodeRequestResult

    data class Failed(val failure: RequestFailure) : CodeRequestResult
}

/** Resultado de confirmar el código de 6 dígitos. */
sealed interface CodeConfirmResult {

    /**
     * 200: el código era correcto. [token] es el `verification_token` (alta) o el `reset_token`
     * (recuperación): vive sólo en memoria y se usa una vez.
     */
    data class Confirmed(val token: String) : CodeConfirmResult {
        override fun toString(): String = "Confirmed(***)"
    }

    /** 400 `INVALID_CODE`: incorrecto, vencido, usado, inexistente o sin intentos (indistinguibles). */
    data object InvalidCode : CodeConfirmResult

    /** 400 `datos_invalidos`: el email, el DNI o el código tienen un formato inválido. */
    data object InvalidInput : CodeConfirmResult

    data class Failed(val failure: RequestFailure) : CodeConfirmResult
}

/** Reglas del código de 6 dígitos que llega por email. */
object EmailCodeRules {

    const val CODE_LENGTH = 6

    /** Deja sólo dígitos y corta en [CODE_LENGTH]: lo que se tipea o se pega en el campo. */
    fun sanitize(raw: String): String = raw.filter(Char::isDigit).take(CODE_LENGTH)

    fun isComplete(code: String): Boolean = code.length == CODE_LENGTH && code.all(Char::isDigit)
}
