package ar.felaufernandezbottaro.vinculovial.domain.push

sealed interface DeleteFcmTokenResult {
    data object Success : DeleteFcmTokenResult
    data class Failed(val error: DeleteFcmTokenError) : DeleteFcmTokenResult
}

/** Sin RateLimited a propósito: la baja no tiene límite en Kong. */
sealed interface DeleteFcmTokenError {
    /** 502/503/504: el Gateway o una dependencia suya no está disponible. Transitorio. */
    data object Unavailable : DeleteFcmTokenError
    data object Network : DeleteFcmTokenError
    data object Unexpected : DeleteFcmTokenError
}
