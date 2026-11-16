package ar.felaufernandezbottaro.vinculovial.presentation.common

import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure

/**
 * Lo que la pantalla de un código por email le muestra al usuario cuando algo falla. Lo comparten
 * la verificación del email (alta) y la recuperación de contraseña.
 */
sealed interface CodeFlowError {

    /** `INVALID_CODE`: incorrecto, vencido, usado o sin intentos. El servidor no distingue. */
    data object InvalidCode : CodeFlowError

    /** El servidor rechazó el formato (`datos_invalidos`): algo que la validación local debió atajar. */
    data object InvalidInput : CodeFlowError

    data class Failure(val failure: RequestFailure) : CodeFlowError
}

/** Textos de US-13 compartidos entre las pantallas de código. En español rioplatense, con voseo. */
object CodeFlowTexts {
    const val INVALID_CODE = "El código no es válido o venció"
    const val INVALID_INPUT = "Revisá los datos ingresados e intentá de nuevo."
    const val RESEND_AVAILABLE = "Reenviar código"

    /** Después de pedir el código de recuperación: idéntico exista o no la cuenta (anti-enumeración). */
    const val RECOVERY_REQUESTED =
        "Si el DNI corresponde a una cuenta, te enviamos un código al email registrado"

    fun resendIn(seconds: Int): String = "Reenviar código en ${seconds}s"
}

fun CodeFlowError.message(): String = when (this) {
    CodeFlowError.InvalidCode -> CodeFlowTexts.INVALID_CODE
    CodeFlowError.InvalidInput -> CodeFlowTexts.INVALID_INPUT
    is CodeFlowError.Failure -> failure.message()
}

fun RequestFailure.message(): String = when (this) {
    RequestFailure.NETWORK ->
        "No pudimos comunicarnos con el servidor. Revisá tu conexión e intentá de nuevo."
    RequestFailure.UNAVAILABLE ->
        "El servidor no está disponible en este momento. Intentá de nuevo en unos minutos."
    RequestFailure.RATE_LIMITED ->
        "Hiciste demasiados intentos. Esperá un momento y probá de nuevo."
    RequestFailure.ATTESTATION_REJECTED ->
        "No pudimos validar la app. Por seguridad sólo aceptamos pedidos desde una instalación " +
            "oficial de ViVi en un dispositivo íntegro."
    RequestFailure.DEVICE_NOT_TRUSTED ->
        "No pudimos validar tu dispositivo. Instalá ViVi desde Google Play en un dispositivo sin modificar."
    RequestFailure.ATTESTATION_UNAVAILABLE ->
        "Necesitamos Google Play activo y conexión a internet. Revisalos e intentá de nuevo."
    RequestFailure.CONFIGURATION ->
        "Falta configuración de seguridad para completar el pedido. Probá más tarde o escribinos."
    RequestFailure.UNEXPECTED ->
        "Algo salió mal. Intentá de nuevo en unos minutos."
}
