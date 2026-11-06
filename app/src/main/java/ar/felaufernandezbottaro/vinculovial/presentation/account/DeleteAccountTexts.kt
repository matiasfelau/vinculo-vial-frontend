package ar.felaufernandezbottaro.vinculovial.presentation.account

import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountError

/**
 * Los textos de la pantalla informativa son los del contrato de US-11 (el de retención, el de la
 * iteración de Audit), palabra por palabra: el párrafo de retención cita la Ley 25.326 y es lo
 * que el usuario acepta al confirmar.
 *
 * Ningún texto de esta pantalla (ni de sus errores) menciona la reputación, las revisiones ni
 * las sanciones de la cuenta: eso no se le muestra al usuario en ningún momento.
 */
object DeleteAccountTexts {
    const val TITLE = "Eliminar cuenta"

    const val WHAT_IS_DELETED =
        "Se borran tu perfil, tus vehículos vinculados, tus sesiones y tus dispositivos."

    const val WHAT_IS_KEPT =
        "Las alertas que enviaste y que llegaron a su destinatario se conservan durante 3 años " +
            "desde su envío, aunque elimines tu cuenta: el tipo, la fecha, las fotos tal como las " +
            "vio el destinatario y, cifrados, tu DNI y la patente. Se guardan aisladas, solo se " +
            "abren ante un pedido judicial (Ley 25.326, art. 16 inc. 5) y después se destruyen."

    const val IRREVERSIBLE = "Esta acción no se puede deshacer."

    val infoParagraphs: List<String> = listOf(WHAT_IS_DELETED, WHAT_IS_KEPT, IRREVERSIBLE)

    const val PASSWORD_LABEL = "CONTRASEÑA"
    const val PASSWORD_REQUIRED = "Ingresá tu contraseña para confirmar."
    const val CONFIRM = "Eliminar mi cuenta"
    const val RETRY = "Reintentar"
}

fun DeleteAccountError.message(): String = when (this) {
    DeleteAccountError.InvalidPassword -> "Contraseña incorrecta"

    DeleteAccountError.SessionExpired -> "Tu sesión venció. Iniciá sesión de nuevo."

    // Mismo texto para el límite de intentos y para un servidor caído: al usuario le toca lo
    // mismo en los dos casos, esperar un rato y reintentar.
    DeleteAccountError.RateLimited,
    DeleteAccountError.Unavailable,
    DeleteAccountError.Unexpected,
    -> "No pudimos eliminar tu cuenta en este momento. Intentá de nuevo en unos minutos."

    DeleteAccountError.Network ->
        "No pudimos comunicarnos con el servidor. Verificá tu conexión e intentá de nuevo."
}

/**
 * Todo error salvo la contraseña incorrecta se resuelve reintentando tal cual: no hay nada
 * que el usuario pueda corregir. (La sesión vencida no se reintenta: la pantalla lo manda a Login.)
 */
fun DeleteAccountError.isRetryable(): Boolean = when (this) {
    DeleteAccountError.RateLimited,
    DeleteAccountError.Unavailable,
    DeleteAccountError.Network,
    DeleteAccountError.Unexpected,
    -> true

    DeleteAccountError.InvalidPassword,
    DeleteAccountError.SessionExpired,
    -> false
}
