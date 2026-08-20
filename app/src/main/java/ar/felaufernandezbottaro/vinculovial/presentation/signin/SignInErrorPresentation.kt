package ar.felaufernandezbottaro.vinculovial.presentation.signin

import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInError

/**
 * A diferencia de US-01, el login no tiene un "editar formulario" separado de la captura:
 * el formulario ES la pantalla, así que cualquier error se resuelve ahí mismo con un solo
 * mensaje inline — no hace falta un diálogo con acciones distintas por tipo de error.
 */
fun SignInError.message(): String = when (this) {
    SignInError.InvalidData -> "Revisá el DNI y la contraseña ingresados."

    // Genérico a propósito, mismo texto sin importar si el DNI no existe o la contraseña
    // no matchea: distinguirlo permitiría usar el login para averiguar qué DNI está
    // registrado (anti-enumeración) — ver el contrato compartido de US-02.
    SignInError.InvalidCredentials -> "DNI o contraseña incorrectos."

    SignInError.AttestationRejected ->
        "Por seguridad sólo aceptamos inicios de sesión desde una instalación oficial de " +
            "ViVi en un dispositivo íntegro."

    SignInError.DeviceNotTrusted ->
        "Instalá ViVi desde Google Play en un dispositivo sin modificar. Reintentar no va " +
            "a cambiar el resultado."

    SignInError.AttestationUnavailable ->
        "Necesitamos Google Play activo y conexión a internet para iniciar sesión. " +
            "Revisalos e intentá de nuevo."

    SignInError.Network ->
        "No pudimos comunicarnos con el servidor. Revisá tu conexión e intentá de nuevo."

    SignInError.Configuration ->
        "La app no está configurada. Probá más tarde o escribinos."

    SignInError.Unexpected ->
        "No pudimos iniciar sesión. Intentá de nuevo en unos minutos."
}
