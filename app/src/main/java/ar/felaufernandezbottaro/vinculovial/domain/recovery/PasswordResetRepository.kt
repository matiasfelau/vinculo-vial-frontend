package ar.felaufernandezbottaro.vinculovial.domain.recovery

import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeConfirmResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeRequestResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure

/**
 * Recuperación de contraseña (US-13). Puerto de salida: la implementación (cifrado HPKE contra
 * Identity + HTTP) vive en `data`.
 */
interface PasswordResetRepository {

    /** `POST /auth/password-resets`: pide el código. Siempre 202 si el DNI es válido. */
    suspend fun requestCode(dni: String): CodeRequestResult

    /** `POST /auth/password-resets/confirm`: devuelve el `reset_token`. */
    suspend fun confirmCode(dni: String, code: String): CodeConfirmResult

    /** `POST /auth/password-resets/complete`: cambia la contraseña. */
    suspend fun complete(request: PasswordResetCompletion): PasswordResetResult
}

/**
 * Lo que viaja en el payload cifrado de `complete`. El DNI, el trámite y el sexo son los que se
 * leyeron del documento; la selfie es la única imagen que sale del dispositivo.
 */
data class PasswordResetCompletion(
    val resetToken: String,
    val dni: String,
    val procedureNumber: String,
    val sex: String,
    val selfieBase64: String,
    val newPassword: String,
) {
    /** Nunca exponer credenciales ni datos del documento en logs ni en reportes de crash. */
    override fun toString(): String = "PasswordResetCompletion(***)"
}

sealed interface PasswordResetResult {

    /** 204: la contraseña cambió y el servidor revocó todas las sesiones. */
    data object Done : PasswordResetResult

    /** 400 `INVALID_RESET_TOKEN`: ausente, vencido, usado o sin intentos. Hay que pedir otro código. */
    data object InvalidResetToken : PasswordResetResult

    /** 400 `identidad_no_verificada`: RENAPER rechazó, o el DNI escaneado no es el de la cuenta. */
    data object IdentityNotVerified : PasswordResetResult

    /** 400 `datos_invalidos` con la contraseña señalada como débil o predecible. */
    data object PasswordRejected : PasswordResetResult

    /** 400 `datos_invalidos` sin una causa que el usuario pueda corregir editando la contraseña. */
    data object InvalidData : PasswordResetResult

    data class Failed(val failure: RequestFailure) : PasswordResetResult
}
