package ar.felaufernandezbottaro.vinculovial.domain.signup

/**
 * Resultado del registro visto desde el dominio. `presentation` nunca ve códigos HTTP
 * ni excepciones crudas: sólo estos casos.
 */
sealed interface SignUpResult {

    /** 201 Created. US-01 no devuelve sesión: el login es US-02. */
    data object Registered : SignUpResult

    data class Failed(val error: SignUpError) : SignUpResult
}

sealed interface SignUpError {

    /** No se encontró un código PDF417 legible en el reverso del documento. */
    data object DocumentUnreadable : SignUpError

    /** Se leyó un código, pero no es el de un DNI argentino. */
    data object DocumentNotRecognized : SignUpError

    /** Liveness negativo: no se pudo comprobar que haya una persona presente. */
    data object LivenessFailed : SignUpError

    /** El DNI leído del documento no coincide con el que se tipeó. */
    data object DniMismatch : SignUpError

    /** 400: el backend rechazó los datos. */
    data object InvalidData : SignUpError

    /** 403: el Gateway rechazó la App Attestation. */
    data object AttestationRejected : SignUpError

    /**
     * El dispositivo no pasó la verificación de integridad local: la app no es una
     * instalación reconocida por Play o el dispositivo está comprometido.
     */
    data object DeviceNotTrusted : SignUpError

    /**
     * No se pudo obtener el veredicto de integridad (sin Play Store, sin red, cuota
     * agotada). A diferencia de [DeviceNotTrusted], reintentar puede funcionar.
     */
    data object AttestationUnavailable : SignUpError

    /**
     * 409: ya existe una cuenta. Deliberadamente sin detalle: no se le informa al
     * usuario si colisionó el DNI o el email (evita enumeración de cuentas).
     */
    data object AlreadyRegistered : SignUpError

    /** Sin conexión, timeout o servidor inalcanzable. */
    data object Network : SignUpError

    /** Falta o está mal la configuración de build (clave HPKE, PSK). */
    data object Configuration : SignUpError

    /** Cualquier otra cosa, incluido 5xx. */
    data object Unexpected : SignUpError
}
