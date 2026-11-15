package ar.felaufernandezbottaro.vinculovial.data.recovery

import ar.felaufernandezbottaro.vinculovial.data.remote.AuthApiService
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.AuthCallOutcome
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.SealedAuthCaller
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.AuthErrorBodyDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.PasswordResetCompletePayloadDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.PasswordResetConfirmPayloadDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.PasswordResetRequestPayloadDto
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeConfirmResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeRequestResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure
import ar.felaufernandezbottaro.vinculovial.domain.recovery.PasswordResetCompletion
import ar.felaufernandezbottaro.vinculovial.domain.recovery.PasswordResetRepository
import ar.felaufernandezbottaro.vinculovial.domain.recovery.PasswordResetResult
import javax.inject.Inject
import javax.inject.Singleton

/** Recuperación de contraseña (US-13). Cifra con HPKE contra Identity y mapea por `code`. */
@Singleton
class PasswordResetRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    private val caller: SealedAuthCaller,
) : PasswordResetRepository {

    override suspend fun requestCode(dni: String): CodeRequestResult =
        when (
            val outcome = caller.call(
                payload = PasswordResetRequestPayloadDto(dni = dni),
                payloadType = PasswordResetRequestPayloadDto::class.java,
                send = api::requestPasswordReset,
            )
        ) {
            is AuthCallOutcome.Success -> CodeRequestResult.Sent
            is AuthCallOutcome.Rejected ->
                if (outcome.code == CODE_INVALID_DATA) CodeRequestResult.InvalidInput
                else CodeRequestResult.Failed(RequestFailure.UNEXPECTED)
            is AuthCallOutcome.Failed -> CodeRequestResult.Failed(outcome.failure)
        }

    override suspend fun confirmCode(dni: String, code: String): CodeConfirmResult =
        when (
            val outcome = caller.call(
                payload = PasswordResetConfirmPayloadDto(dni = dni, code = code),
                payloadType = PasswordResetConfirmPayloadDto::class.java,
                send = api::confirmPasswordReset,
            )
        ) {
            is AuthCallOutcome.Success -> outcome.body?.resetToken
                ?.takeIf { it.isNotBlank() }
                ?.let(CodeConfirmResult::Confirmed)
                ?: CodeConfirmResult.Failed(RequestFailure.UNEXPECTED)

            is AuthCallOutcome.Rejected -> when (outcome.code) {
                CODE_INVALID_CODE -> CodeConfirmResult.InvalidCode
                CODE_INVALID_DATA -> CodeConfirmResult.InvalidInput
                else -> CodeConfirmResult.Failed(RequestFailure.UNEXPECTED)
            }

            is AuthCallOutcome.Failed -> CodeConfirmResult.Failed(outcome.failure)
        }

    override suspend fun complete(request: PasswordResetCompletion): PasswordResetResult =
        when (
            val outcome = caller.call(
                payload = PasswordResetCompletePayloadDto(
                    resetToken = request.resetToken,
                    dni = request.dni,
                    procedureNumber = request.procedureNumber,
                    sex = request.sex,
                    selfie = request.selfieBase64,
                    newPassword = request.newPassword,
                ),
                payloadType = PasswordResetCompletePayloadDto::class.java,
                send = api::completePasswordReset,
            )
        ) {
            // El contrato responde 204; cualquier 2xx significa que la contraseña cambió.
            is AuthCallOutcome.Success -> PasswordResetResult.Done

            is AuthCallOutcome.Rejected -> when (outcome.code) {
                CODE_INVALID_RESET_TOKEN -> PasswordResetResult.InvalidResetToken
                CODE_IDENTITY_NOT_VERIFIED -> PasswordResetResult.IdentityNotVerified
                CODE_INVALID_DATA ->
                    if (outcome.body.hasPasswordRejection()) PasswordResetResult.PasswordRejected
                    else PasswordResetResult.InvalidData
                else -> PasswordResetResult.InvalidData
            }

            is AuthCallOutcome.Failed -> PasswordResetResult.Failed(outcome.failure)
        }

    /**
     * `datos_invalidos` con la contraseña señalada por débil o predecible: sólo el servidor lo
     * sabe (similitud con el email y el DNI incluida) y es lo único que el usuario corrige
     * editando la contraseña. Un campo de captura (`dni`, `tramite`, `sexo`, `selfie`) rechazado
     * gana: arreglar la contraseña no sirve si la captura igual se va a rechazar.
     */
    private fun AuthErrorBodyDto?.hasPasswordRejection(): Boolean {
        val details = this?.details.orEmpty()
        if (details.any { it.field in CAPTURE_FIELDS }) return false
        return details.any { it.code in PASSWORD_REJECTION_CODES }
    }

    private companion object {
        const val CODE_INVALID_CODE = "INVALID_CODE"
        const val CODE_INVALID_RESET_TOKEN = "INVALID_RESET_TOKEN"
        const val CODE_IDENTITY_NOT_VERIFIED = "identidad_no_verificada"
        const val CODE_INVALID_DATA = "datos_invalidos"

        val CAPTURE_FIELDS = setOf("dni", "tramite", "sexo", "selfie")
        val PASSWORD_REJECTION_CODES = setOf("password_debil", "password_predecible")
    }
}
