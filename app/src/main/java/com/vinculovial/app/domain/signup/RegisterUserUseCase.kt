package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.CredentialRules
import com.vinculovial.app.domain.signup.model.Credentials
import com.vinculovial.app.domain.signup.model.EdgeResult
import com.vinculovial.app.domain.signup.model.SignUpForm
import javax.inject.Inject

/**
 * Orquesta US-01 completo: valida el formulario, corre el onboarding biométrico (que
 * descarta las imágenes al terminar), contrasta el DNI leído del documento con el
 * tipeado y recién ahí manda el registro cifrado.
 *
 * El orden importa: nada sale del dispositivo si la verificación local no cierra.
 */
class RegisterUserUseCase @Inject constructor(
    private val identityVerification: IdentityVerificationService,
    private val repository: SignUpRepository,
) {

    suspend operator fun invoke(form: SignUpForm): SignUpResult {
        // Última barrera de validación: la UI ya validó, pero el dominio no confía en ella.
        if (CredentialRules.problems(form).isNotEmpty()) {
            return SignUpResult.Failed(SignUpError.InvalidData)
        }

        return when (val edgeResult = identityVerification.verifyAndDiscard()) {
            is EdgeResult.Rejected -> SignUpResult.Failed(edgeResult.reason.toSignUpError())

            is EdgeResult.Verified -> {
                if (!edgeResult.document.number.matches(form.dni)) {
                    return SignUpResult.Failed(SignUpError.DniMismatch)
                }

                // Viajan los datos que se leyeron del documento, no lo que se tipeó. La
                // identidad ya no se confirma acá: el backend la verifica contra RENAPER con
                // la selfie que se manda en el mismo payload.
                repository.signUp(
                    Credentials(
                        dni = edgeResult.document.number,
                        procedureNumber = edgeResult.document.procedureNumber,
                        sex = edgeResult.document.sex,
                        email = form.email.trim(),
                        password = form.password,
                        selfieBase64 = edgeResult.selfieBase64,
                    ),
                )
            }
        }
    }

    private fun String.matches(declared: String): Boolean =
        CredentialRules.normalizeDni(this) == CredentialRules.normalizeDni(declared)

    private fun EdgeResult.Reason.toSignUpError(): SignUpError = when (this) {
        EdgeResult.Reason.BARCODE_NOT_FOUND,
        EdgeResult.Reason.MISSING_PROCEDURE_NUMBER,
        -> SignUpError.DocumentUnreadable
        EdgeResult.Reason.BARCODE_NOT_A_DNI -> SignUpError.DocumentNotRecognized
        EdgeResult.Reason.LIVENESS_FAILED -> SignUpError.LivenessFailed
        EdgeResult.Reason.INCOMPLETE_CAPTURE -> SignUpError.DocumentUnreadable
    }
}
