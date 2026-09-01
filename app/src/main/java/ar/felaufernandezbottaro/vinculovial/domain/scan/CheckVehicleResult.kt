package ar.felaufernandezbottaro.vinculovial.domain.scan

/**
 * Resultado de `/vehicles/check` visto desde el dominio. `presentation` nunca ve un código
 * HTTP ni una excepción cruda: sólo estos casos.
 */
sealed interface CheckVehicleResult {

    /**
     * 200: el vehículo está en la red. Lleva el mismo `pat_enc` ya generado para esta
     * patente -- `SendAlertUseCase` lo reusa tal cual, sin volver a cifrar.
     */
    data class Found(val patEnc: String) : CheckVehicleResult

    /** 404: el vehículo no está en la red. La UI muestra un mensaje neutro, sin ofrecer alertar igual. */
    data object NotInNetwork : CheckVehicleResult

    data class Failed(val error: CheckVehicleError) : CheckVehicleResult
}

sealed interface CheckVehicleError {

    /** 429: límite de Kong alcanzado (5 req/min, 10 req/hora por `X-User-Id`). */
    data object RateLimited : CheckVehicleError

    /** Falta o está mal la configuración de build (clave HPKE de Vehicle). */
    data object Configuration : CheckVehicleError

    /** Sin conexión, timeout o servidor inalcanzable. */
    data object Network : CheckVehicleError

    /** Cualquier otra cosa, incluido 5xx y un 401 que TokenAuthenticator no pudo resolver. */
    data object Unexpected : CheckVehicleError
}
