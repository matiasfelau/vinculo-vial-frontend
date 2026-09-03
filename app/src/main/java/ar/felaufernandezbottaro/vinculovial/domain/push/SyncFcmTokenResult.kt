package ar.felaufernandezbottaro.vinculovial.domain.push

sealed interface SyncFcmTokenResult {
    data object Success : SyncFcmTokenResult
    data class Failed(val error: SyncFcmTokenError) : SyncFcmTokenResult
}

sealed interface SyncFcmTokenError {
    /** Kong limita el alta a 10/día por usuario. */
    data object RateLimited : SyncFcmTokenError
    data object Network : SyncFcmTokenError
    data object Unexpected : SyncFcmTokenError
}
