package ar.felaufernandezbottaro.vinculovial.data.emailverification

import ar.felaufernandezbottaro.vinculovial.data.remote.AuthApiService
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.AuthCallOutcome
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.SealedAuthCaller
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.EmailVerificationConfirmPayloadDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.EmailVerificationRequestPayloadDto
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeConfirmResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeRequestResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure
import ar.felaufernandezbottaro.vinculovial.domain.emailverification.EmailVerificationRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Verificación del email en el alta (US-13). Cifra con HPKE contra Identity y mapea por `code`. */
@Singleton
class EmailVerificationRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    private val caller: SealedAuthCaller,
) : EmailVerificationRepository {

    override suspend fun requestCode(email: String): CodeRequestResult =
        when (
            val outcome = caller.call(
                payload = EmailVerificationRequestPayloadDto(email = email),
                payloadType = EmailVerificationRequestPayloadDto::class.java,
                send = api::requestEmailVerification,
            )
        ) {
            is AuthCallOutcome.Success -> CodeRequestResult.Sent
            is AuthCallOutcome.Rejected ->
                if (outcome.code == CODE_INVALID_DATA) CodeRequestResult.InvalidInput
                else CodeRequestResult.Failed(RequestFailure.UNEXPECTED)
            is AuthCallOutcome.Failed -> CodeRequestResult.Failed(outcome.failure)
        }

    override suspend fun confirmCode(email: String, code: String): CodeConfirmResult =
        when (
            val outcome = caller.call(
                payload = EmailVerificationConfirmPayloadDto(email = email, code = code),
                payloadType = EmailVerificationConfirmPayloadDto::class.java,
                send = api::confirmEmailVerification,
            )
        ) {
            is AuthCallOutcome.Success -> outcome.body?.verificationToken
                ?.takeIf { it.isNotBlank() }
                ?.let(CodeConfirmResult::Confirmed)
                // 2xx sin token: contrato roto, no se inventa un resultado.
                ?: CodeConfirmResult.Failed(RequestFailure.UNEXPECTED)

            is AuthCallOutcome.Rejected -> when (outcome.code) {
                CODE_INVALID_CODE -> CodeConfirmResult.InvalidCode
                CODE_INVALID_DATA -> CodeConfirmResult.InvalidInput
                else -> CodeConfirmResult.Failed(RequestFailure.UNEXPECTED)
            }

            is AuthCallOutcome.Failed -> CodeConfirmResult.Failed(outcome.failure)
        }

    private companion object {
        const val CODE_INVALID_CODE = "INVALID_CODE"
        const val CODE_INVALID_DATA = "datos_invalidos"
    }
}
