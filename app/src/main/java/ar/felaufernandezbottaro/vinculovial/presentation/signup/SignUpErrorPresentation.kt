package ar.felaufernandezbottaro.vinculovial.presentation.signup

import ar.felaufernandezbottaro.vinculovial.domain.signup.SignUpError

/**
 * Qué puede hacer el usuario con el error: define el botón principal del diálogo.
 */
enum class SignUpErrorAction {
    /** Repetir la captura de las imágenes. */
    RETRY_CAPTURE,

    /** Volver al formulario a corregir datos. */
    EDIT_FORM,

    /** Sólo cerrar: no hay nada que el usuario pueda corregir ahora. */
    DISMISS,
}

/**
 * Presentación de un error de dominio.
 *
 * Todos los mensajes son genéricos a propósito. En particular, el 409 no distingue
 * si colisionó el DNI o el email: informarlo permitiría enumerar cuentas existentes.
 */
data class SignUpErrorPresentation(
    val title: String,
    val message: String,
    val action: SignUpErrorAction,
)

fun SignUpError.presentation(): SignUpErrorPresentation = when (this) {
    // Ni "frente" ni "reverso": el escaneo detecta PDF417 (tarjeta) y QR (con chip) al
    // mismo tiempo, así que el lado correcto depende de qué versión de DNI tenga el
    // usuario. Tampoco se pide acercarse — el lente pierde el foco antes de que el
    // documento llene la pantalla, así que la instrucción útil es apoyarlo y esperar el foco.
    SignUpError.DocumentUnreadable -> SignUpErrorPresentation(
        title = "No pudimos leer tu DNI",
        message = "Apoyá el documento en una superficie plana, con buena luz y sin reflejos, " +
            "y esperá a que se vea nítido antes de sacar la foto.",
        action = SignUpErrorAction.RETRY_CAPTURE,
    )

    SignUpError.DocumentNotRecognized -> SignUpErrorPresentation(
        title = "El documento no es válido",
        message = "Sólo podemos validar un DNI argentino vigente. Revisá que estés " +
            "fotografiando el documento correcto.",
        action = SignUpErrorAction.RETRY_CAPTURE,
    )

    SignUpError.LivenessFailed -> SignUpErrorPresentation(
        title = "No pudimos confirmar que seas vos",
        message = "Repetí la selfie siguiendo la indicación en pantalla, mirando de frente a la " +
            "cámara y sin anteojos de sol ni gorra.",
        action = SignUpErrorAction.RETRY_CAPTURE,
    )

    SignUpError.DniMismatch -> SignUpErrorPresentation(
        title = "Los datos no coinciden",
        message = "El número que leímos en el documento no es el que ingresaste. Revisá el " +
            "DNI del formulario o volvé a sacar la foto.",
        action = SignUpErrorAction.EDIT_FORM,
    )

    SignUpError.InvalidData -> SignUpErrorPresentation(
        title = "Revisá tus datos",
        message = "Alguno de los datos ingresados no es válido. Corregilos e intentá de nuevo.",
        action = SignUpErrorAction.EDIT_FORM,
    )

    SignUpError.AttestationRejected -> SignUpErrorPresentation(
        title = "No pudimos validar la app",
        message = "Por seguridad sólo aceptamos registros desde una instalación oficial de ViVi " +
            "en un dispositivo íntegro.",
        action = SignUpErrorAction.DISMISS,
    )

    SignUpError.DeviceNotTrusted -> SignUpErrorPresentation(
        title = "No pudimos validar tu dispositivo",
        message = "Instalá ViVi desde Google Play en un dispositivo sin modificar. Reintentar " +
            "no va a cambiar el resultado.",
        action = SignUpErrorAction.DISMISS,
    )

    SignUpError.AttestationUnavailable -> SignUpErrorPresentation(
        title = "No pudimos verificar tu dispositivo",
        message = "Necesitamos Google Play activo y conexión a internet para completar el " +
            "registro. Revisalos e intentá de nuevo.",
        action = SignUpErrorAction.RETRY_CAPTURE,
    )

    // Sin detalle: no se le informa al usuario qué dato ya existe.
    SignUpError.AlreadyRegistered -> SignUpErrorPresentation(
        title = "No pudimos crear la cuenta",
        message = "No es posible registrarse con los datos ingresados. Si ya tenés una cuenta, " +
            "iniciá sesión.",
        action = SignUpErrorAction.EDIT_FORM,
    )

    SignUpError.Network -> SignUpErrorPresentation(
        title = "Sin conexión",
        message = "No pudimos comunicarnos con el servidor. Revisá tu conexión e intentá de nuevo.",
        action = SignUpErrorAction.RETRY_CAPTURE,
    )

    SignUpError.Configuration -> SignUpErrorPresentation(
        title = "La app no está configurada",
        message = "Falta configuración de seguridad para completar el registro. Probá más tarde " +
            "o escribinos.",
        action = SignUpErrorAction.DISMISS,
    )

    SignUpError.Unexpected -> SignUpErrorPresentation(
        title = "Algo salió mal",
        message = "No pudimos completar el registro. Intentá de nuevo en unos minutos.",
        action = SignUpErrorAction.RETRY_CAPTURE,
    )
}
