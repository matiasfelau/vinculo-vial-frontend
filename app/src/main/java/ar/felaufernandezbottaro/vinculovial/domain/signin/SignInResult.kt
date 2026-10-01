package ar.felaufernandezbottaro.vinculovial.domain.signin

/**
 * Resultado del login visto desde el dominio. `presentation` nunca ve códigos HTTP ni
 * excepciones crudas: sólo estos casos.
 */
sealed interface SignInResult {

    /** 200: el par de tokens ya quedó persistido en `SecureTokenStore`. */
    data object SignedIn : SignInResult

    data class Failed(val error: SignInError) : SignInResult
}

sealed interface SignInError {

    /** 400: Fail-Fast del backend (DNI con formato inválido, contraseña vacía). */
    data object InvalidData : SignInError

    /**
     * 401: credenciales incorrectas. Mensaje único y genérico por diseño — el backend no
     * distingue "no existe el DNI" de "la contraseña no matchea" (anti-enumeración, mismo
     * body y mismo timing en los dos casos), y la UI tampoco debe inventar esa distinción.
     */
    data object InvalidCredentials : SignInError

    /** 403: el Gateway rechazó la App Attestation antes de que la request llegue a Identity. */
    data object AttestationRejected : SignInError

    /**
     * El dispositivo no pasó la verificación de integridad local: la app no es una
     * instalación reconocida por Play o el dispositivo está comprometido.
     */
    data object DeviceNotTrusted : SignInError

    /**
     * No se pudo obtener el veredicto de integridad (sin Play Store, sin red, cuota
     * agotada). A diferencia de [DeviceNotTrusted], reintentar puede funcionar.
     */
    data object AttestationUnavailable : SignInError

    /** 502/503/504: el Gateway o una dependencia suya no está disponible. Transitorio. */
    data object Unavailable : SignInError

    /** Sin conexión, timeout o servidor inalcanzable. */
    data object Network : SignInError

    /** Falta o está mal la configuración de build (clave HPKE, PSK). */
    data object Configuration : SignInError

    /** Cualquier otra cosa, incluido 500. */
    data object Unexpected : SignInError
}
