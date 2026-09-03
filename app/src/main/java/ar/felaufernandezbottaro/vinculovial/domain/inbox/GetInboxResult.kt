package ar.felaufernandezbottaro.vinculovial.domain.inbox

/**
 * Resultado de una consulta a `/alerts/inbox` visto desde el dominio. `presentation` nunca ve
 * un código HTTP ni una excepción cruda: sólo estos casos.
 */
sealed interface GetInboxResult {

    data class Success(val page: InboxPage) : GetInboxResult

    data class Failed(val error: InboxError) : GetInboxResult
}

sealed interface InboxError {

    /** 429: límite de Kong alcanzado (20 req/min por `X-User-Id`). */
    data object RateLimited : InboxError

    /** Sin conexión, timeout o servidor inalcanzable. */
    data object Network : InboxError

    /** Cualquier otra cosa, incluido 5xx y un 401 que `TokenAuthenticator` no pudo resolver. */
    data object Unexpected : InboxError
}
