package ar.felaufernandezbottaro.vinculovial.presentation.session

/** Estado único de la pantalla de Desbloqueo (US-12). */
data class UnlockUiState(
    val status: UnlockStatus = UnlockStatus.Idle,
) {
    /** Hay algo en curso: el prompt está abierto o se está recuperando la sesión. */
    val isBusy: Boolean get() = status is UnlockStatus.Preparing ||
        status is UnlockStatus.Authenticating ||
        status is UnlockStatus.Refreshing

    val failure: UnlockFailure? get() = (status as? UnlockStatus.Failed)?.failure
}

sealed interface UnlockStatus {
    data object Idle : UnlockStatus

    /** Armando el `Cipher` para el prompt. */
    data object Preparing : UnlockStatus

    /** El prompt biométrico está en pantalla. */
    data object Authenticating : UnlockStatus

    /** Huella verificada: abriendo el blob, pidiendo el refresh y volviendo a envolver. */
    data object Refreshing : UnlockStatus

    data class Failed(val failure: UnlockFailure) : UnlockStatus
}

enum class UnlockFailure {
    /** Sin red, 5xx o breaker abierto: la sesión sigue bloqueada y se puede reintentar. */
    NETWORK,

    /** Demasiados intentos fallidos con la huella: el sistema la bloqueó un rato. */
    LOCKED_OUT,

    /** El prompt o el descifrado fallaron por otro motivo. */
    BIOMETRIC_ERROR,
}
