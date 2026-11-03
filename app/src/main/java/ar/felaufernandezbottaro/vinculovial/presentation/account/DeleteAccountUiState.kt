package ar.felaufernandezbottaro.vinculovial.presentation.account

import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountError

/** Estado único de la pantalla "Eliminar cuenta" (US-11). */
data class DeleteAccountUiState(
    val password: String = "",
    /** Se intentó confirmar con el campo vacío: Fail-Fast, no se llama al backend. */
    val passwordMissing: Boolean = false,
    val status: DeleteAccountStatus = DeleteAccountStatus.Idle,
) {
    val isSubmitting: Boolean get() = status is DeleteAccountStatus.Submitting

    val error: DeleteAccountError? get() = (status as? DeleteAccountStatus.Failed)?.error

    /** Nunca exponer la contraseña en logs ni en reportes de crash. */
    override fun toString(): String =
        "DeleteAccountUiState(password=***, passwordMissing=$passwordMissing, status=$status)"
}

sealed interface DeleteAccountStatus {
    /** Leyendo el aviso y tipeando la contraseña. */
    data object Idle : DeleteAccountStatus

    /** `DELETE /users/me` en curso, o la limpieza local que le sigue. */
    data object Submitting : DeleteAccountStatus

    /** 204 y limpieza local hecha: la pantalla navega a la bienvenida. */
    data object Deleted : DeleteAccountStatus

    data class Failed(val error: DeleteAccountError) : DeleteAccountStatus
}
