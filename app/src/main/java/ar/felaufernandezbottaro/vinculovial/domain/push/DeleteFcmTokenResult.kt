package ar.felaufernandezbottaro.vinculovial.domain.push

sealed interface DeleteFcmTokenResult {
    data object Success : DeleteFcmTokenResult
    data class Failed(val error: DeleteFcmTokenError) : DeleteFcmTokenResult
}

/** Sin RateLimited a propósito: la baja no tiene límite en Kong. */
sealed interface DeleteFcmTokenError {
    data object Network : DeleteFcmTokenError
    data object Unexpected : DeleteFcmTokenError
}
