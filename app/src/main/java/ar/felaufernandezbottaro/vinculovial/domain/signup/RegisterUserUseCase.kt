package ar.felaufernandezbottaro.vinculovial.domain.signup

import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialRules
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.Credentials
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.EdgeResult
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.SignUpForm
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

    suspend operator fun invoke(form: SignUpForm, emailVerificationToken: String): SignUpResult {
        // Última barrera de validación: la UI ya validó, pero el dominio no confía en ella.
        if (CredentialRules.problems(form).isNotEmpty()) {
            return SignUpResult.Failed(SignUpError.InvalidData)
        }
        // US-19: sin la casilla de los Términos no se sigue; el servidor igual rechazaría el alta.
        if (!form.termsAccepted) {
            identityVerification.discardCaptures()
            return SignUpResult.Failed(SignUpError.TermsNotAccepted)
        }
        // Sin el token de la verificación del email no se llega a RENAPER: el servidor lo rechazaría.
        if (emailVerificationToken.isBlank()) {
            identityVerification.discardCaptures()
            return SignUpResult.Failed(SignUpError.EmailNotVerified)
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
                        emailVerificationToken = emailVerificationToken,
                    ),
                )
            }
        }
    }

    private fun String.matches(declared: String): Boolean =
        CredentialRules.normalizeDni(this) == CredentialRules.normalizeDni(declared)

    private fun EdgeResult.Reason.toSignUpError(): SignUpError = when (this) {
        EdgeResult.Reason.LIVENESS_FAILED -> SignUpError.LivenessFailed
        // Las dos restantes sólo se dan si algo se perdió entre pantallas (o la selfie salió
        // ilegible): en los dos casos la salida es rehacer la captura, no editar el
        // formulario. Los motivos de lectura del documento ya no llegan hasta acá — el
        // escaneo en vivo no deja avanzar sin un documento utilizable.
        EdgeResult.Reason.INCOMPLETE_CAPTURE,
        EdgeResult.Reason.SELFIE_UNUSABLE,
        -> SignUpError.DocumentUnreadable
    }
}
