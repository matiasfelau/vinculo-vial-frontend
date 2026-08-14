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
                if (!edgeResult.dni.matches(form.dni)) {
                    return SignUpResult.Failed(SignUpError.DniMismatch)
                }

                // Viaja el DNI que se leyó del documento, no el que se tipeó. De toda la
                // verificación biométrica sólo sale el booleano.
                repository.signUp(
                    Credentials(
                        dni = edgeResult.dni,
                        email = form.email.trim(),
                        password = form.password,
                        biometricVerified = true,
                    ),
                )
            }
        }
    }

    private fun String.matches(declared: String): Boolean =
        CredentialRules.normalizeDni(this) == CredentialRules.normalizeDni(declared)

    private fun EdgeResult.Reason.toSignUpError(): SignUpError = when (this) {
        EdgeResult.Reason.BARCODE_NOT_FOUND -> SignUpError.DocumentUnreadable
        EdgeResult.Reason.BARCODE_NOT_A_DNI -> SignUpError.DocumentNotRecognized
        EdgeResult.Reason.NO_FACE_IN_DOCUMENT,
        EdgeResult.Reason.NO_FACE_IN_SELFIE,
        -> SignUpError.FaceNotDetected
        EdgeResult.Reason.LIVENESS_FAILED -> SignUpError.LivenessFailed
        EdgeResult.Reason.FACE_MISMATCH -> SignUpError.FaceMismatch
        EdgeResult.Reason.BIOMETRICS_UNAVAILABLE -> SignUpError.BiometricsUnavailable
        EdgeResult.Reason.INCOMPLETE_CAPTURE -> SignUpError.DocumentUnreadable
    }
}
