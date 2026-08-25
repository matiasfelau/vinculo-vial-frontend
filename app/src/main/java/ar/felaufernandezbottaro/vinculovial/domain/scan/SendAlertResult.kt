package ar.felaufernandezbottaro.vinculovial.domain.scan

/**
 * Resultado de `/alerts` visto desde el dominio. `presentation` nunca ve un código HTTP ni
 * una excepción cruda: sólo estos casos.
 */
sealed interface SendAlertResult {

    /**
     * 202: la app NO debe mostrar a cuántos dueños llegó ni si llegó -- eso rompería el
     * anonimato de extremo a extremo del contrato. La UI muestra una confirmación genérica.
     */
    data object Sent : SendAlertResult

    data class Failed(val error: SendAlertError) : SendAlertResult
}

sealed interface SendAlertError {

    /** 400: payload mal formado o criptograma inválido. */
    data object InvalidData : SendAlertError

    /** 429: límite de Kong alcanzado (5 req/min, 10 req/hora por `X-User-Id`). No reintentar automático. */
    data object RateLimited : SendAlertError

    /** Sin conexión, timeout o servidor inalcanzable. */
    data object Network : SendAlertError

    /** Cualquier otra cosa, incluido 5xx y un 401 que TokenAuthenticator no pudo resolver. */
    data object Unexpected : SendAlertError
}
