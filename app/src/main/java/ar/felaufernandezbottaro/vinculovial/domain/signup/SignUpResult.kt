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

    /**
     * 400 genérico: el backend rechazó los datos y, o no trajo detalle por campo, o el
     * único campo señalado es del formulario (`email`/`password`) sin ser una contraseña
     * débil/predecible — algo que `CredentialRules` ya debería haber atajado del lado del
     * cliente. Volver al formulario es la única acción con sentido.
     */
    data object InvalidData : SignUpError

    /**
     * 400 (`datos_invalidos`) con al menos un campo que no viene del formulario — `dni`,
     * `tramite`, `sexo` o `selfie`, todos leídos del documento/la selfie, no tipeados. No
     * necesariamente significa que la foto esté mal (puede ser un dato que el backend
     * esperaba en otro formato), pero no hay nada editable en un formulario para esto: la
     * única acción posible es repetir la captura.
     */
    data object DocumentDataRejected : SignUpError

    /**
     * 400 (`datos_invalidos`) con la contraseña rechazada por débil o predecible (p. ej.
     * filtrada en brechas conocidas) — una validación que sólo puede hacerse del lado del
     * servidor, más estricta que `CredentialRules`. A diferencia de [InvalidData], acá sí
     * vale la pena decir específicamente qué corregir: el usuario no tiene forma de
     * adivinarlo solo.
     */
    data object PasswordRejected : SignUpError

    /**
     * 400 (`identidad_no_verificada`): RENAPER no pudo confirmar que la selfie corresponda
     * al documento. Deliberadamente genérico, sin distinguir "no es la misma persona" de
     * "no se pudo verificar" — esa distinción es información que un atacante puede usar
     * para calibrar intentos, y el backend tampoco la manda (sin `details`).
     */
    data object IdentityNotVerified : SignUpError

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
