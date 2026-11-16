package ar.felaufernandezbottaro.vinculovial.domain.recovery

import ar.felaufernandezbottaro.vinculovial.domain.signup.IdentityVerificationService
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialField
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialRules
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.EdgeResult
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.SignUpForm
import javax.inject.Inject

/**
 * Cierra la recuperación: valida la contraseña nueva, toma lo que dejó la captura (documento y
 * selfie, descartándolas al terminar) y manda el `complete`.
 *
 * El DNI escaneado se compara con el ingresado antes de enviar: si no coinciden, no se llama al
 * backend. El servidor lo vuelve a validar contra la cuenta.
 */
class CompletePasswordResetUseCase @Inject constructor(
    private val identityVerification: IdentityVerificationService,
    private val repository: PasswordResetRepository,
) {

    sealed interface Outcome {
        data class Finished(val result: PasswordResetResult) : Outcome

        /** El DNI del documento no es el que se ingresó: no se llamó al backend. */
        data object DniMismatch : Outcome

        /** La prueba de vida no se superó. */
        data object LivenessFailed : Outcome

        /** Falta una captura o la selfie quedó inservible: hay que repetir la captura. */
        data object CaptureUnusable : Outcome

        /** La contraseña nueva no cumple las reglas del alta. */
        data object InvalidPassword : Outcome
    }

    suspend operator fun invoke(
        resetToken: String,
        enteredDni: String,
        newPassword: String,
    ): Outcome {
        // Última barrera: la UI ya validó, pero el dominio no confía en ella.
        val form = SignUpForm(password = newPassword, passwordConfirmation = newPassword)
        if (CredentialRules.problemOf(CredentialField.PASSWORD, form) != null) return Outcome.InvalidPassword

        return when (val edge = identityVerification.verifyAndDiscard()) {
            is EdgeResult.Rejected -> when (edge.reason) {
                EdgeResult.Reason.LIVENESS_FAILED -> Outcome.LivenessFailed
                EdgeResult.Reason.INCOMPLETE_CAPTURE,
                EdgeResult.Reason.SELFIE_UNUSABLE,
                -> Outcome.CaptureUnusable
            }

            is EdgeResult.Verified -> {
                if (!documentMatches(edge.document.number, enteredDni)) return Outcome.DniMismatch

                // Viajan los datos leídos del documento, no lo que se tipeó.
                Outcome.Finished(
                    repository.complete(
                        PasswordResetCompletion(
                            resetToken = resetToken,
                            dni = edge.document.number,
                            procedureNumber = edge.document.procedureNumber,
                            sex = edge.document.sex,
                            selfieBase64 = edge.selfieBase64,
                            newPassword = newPassword,
                        ),
                    ),
                )
            }
        }
    }

    companion object {
        /** Compara dos números de documento sin que los ceros a la izquierda los distingan. */
        fun documentMatches(scanned: String, entered: String): Boolean =
            CredentialRules.normalizeDni(scanned) == CredentialRules.normalizeDni(entered)
    }
}
