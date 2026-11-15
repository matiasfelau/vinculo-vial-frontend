package ar.felaufernandezbottaro.vinculovial.presentation.session

import ar.felaufernandezbottaro.vinculovial.domain.session.LoginNotice

/**
 * Los textos de la sesión bloqueada y del desbloqueo (US-12). Los dos avisos de [LoginNotice] son
 * los del contrato, palabra por palabra.
 */
object SessionTexts {
    const val SESSION_EXPIRED = "Tu sesión venció"
    const val BIOMETRICS_CHANGED = "Cambiaron las huellas del dispositivo. Ingresá con tu contraseña"

    const val UNLOCK_TITLE = "Desbloqueá Vínculo Vial"
    const val UNLOCK_SUBTITLE = "Usá tu huella para volver a entrar."
    const val UNLOCK_BUTTON = "Usar huella"
    const val USE_PASSWORD_BUTTON = "Usar contraseña"
    const val UNLOCK_RETRY_BUTTON = "Reintentar"

    const val NETWORK_ERROR = "No pudimos conectarnos. Revisá tu conexión y reintentá: no hace falta tu contraseña."
    const val LOCKED_OUT_ERROR = "Hubo demasiados intentos con la huella. Esperá un momento o usá tu contraseña."
    const val BIOMETRIC_ERROR = "No pudimos verificar tu huella. Probá de nuevo o usá tu contraseña."

    const val PROMPT_TITLE = "Desbloquear Vínculo Vial"
    const val PROMPT_SUBTITLE = "Confirmá tu identidad con tu huella"
    const val PROMPT_CANCEL = "Cancelar"

    const val OFFER_TITLE = "¿Entrar con tu huella?"
    const val OFFER_TEXT = "La próxima vez que abras la app podés desbloquearla con tu huella, sin escribir la " +
        "contraseña. Podés desactivarlo cuando quieras desde la pantalla principal."
    const val OFFER_ACCEPT = "Activar"
    const val OFFER_DISMISS = "Ahora no"

    const val SETTING_TITLE = "Desbloquear con huella"
    const val SETTING_SUBTITLE = "Entrá sin escribir la contraseña"
    const val ACTIVATION_FAILED = "No pudimos activar la huella en este dispositivo. Seguís entrando con tu contraseña."
    const val ACTIVATION_FAILED_DISMISS = "Entendido"
}

fun LoginNotice.message(): String = when (this) {
    LoginNotice.SESSION_EXPIRED -> SessionTexts.SESSION_EXPIRED
    LoginNotice.BIOMETRICS_CHANGED -> SessionTexts.BIOMETRICS_CHANGED
}

fun UnlockFailure.message(): String = when (this) {
    UnlockFailure.NETWORK -> SessionTexts.NETWORK_ERROR
    UnlockFailure.LOCKED_OUT -> SessionTexts.LOCKED_OUT_ERROR
    UnlockFailure.BIOMETRIC_ERROR -> SessionTexts.BIOMETRIC_ERROR
}
