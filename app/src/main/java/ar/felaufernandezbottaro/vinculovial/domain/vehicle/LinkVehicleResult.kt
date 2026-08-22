package ar.felaufernandezbottaro.vinculovial.domain.vehicle

/**
 * Resultado de la vinculación visto desde el dominio. `presentation` nunca ve un código
 * HTTP ni una excepción cruda: sólo estos casos.
 */
sealed interface LinkVehicleResult {

    /** 201 Created. */
    data object Linked : LinkVehicleResult

    data class Failed(val error: LinkVehicleError) : LinkVehicleResult
}

sealed interface LinkVehicleError {

    /**
     * 403: el DNI de la cédula no corresponde al dueño de la sesión activa (`match: false`
     * en `/internal/verify-ownership`). El mensaje que se le muestre al usuario no debe
     * sugerir reintentar con otros datos ni insinuar que el DNI existe en el sistema.
     */
    data object OwnerMismatch : LinkVehicleError

    /** 409: ya existe el vínculo para el par (owner_id, patente) de esta cuenta. */
    data object AlreadyLinked : LinkVehicleError

    /** 400: payload mal formado o criptograma inválido. */
    data object InvalidData : LinkVehicleError

    /** Falta o está mal la configuración de build (clave HPKE de Identity o Vehicle). */
    data object Configuration : LinkVehicleError

    /** Sin conexión, timeout o servidor inalcanzable. */
    data object Network : LinkVehicleError

    /** Cualquier otra cosa, incluido 5xx y un 401 que TokenAuthenticator no pudo resolver. */
    data object Unexpected : LinkVehicleError
}
