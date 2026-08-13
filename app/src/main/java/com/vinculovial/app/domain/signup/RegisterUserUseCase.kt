package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.CredentialRules
import com.vinculovial.app.domain.signup.model.Credentials
import com.vinculovial.app.domain.signup.model.SignUpForm
import javax.inject.Inject

/**
 * Orquesta US-01 completo: valida el formulario, corre el Edge (que descarta las
 * imágenes al terminar), contrasta el DNI leído con el tipeado y recién ahí manda
 * el registro cifrado.
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

        val edgeResult = identityVerification.verifyAndDiscard()

        val documentDni = edgeResult.dni
            ?: return SignUpResult.Failed(SignUpError.DocumentUnreadable)

        if (!edgeResult.isLive) {
            return SignUpResult.Failed(SignUpError.LivenessFailed)
        }

        if (documentDni != form.dni) {
            return SignUpResult.Failed(SignUpError.DniMismatch)
        }

        // Viaja el DNI que leyó el documento, no el que se tipeó.
        return repository.signUp(
            Credentials(
                dni = documentDni,
                email = form.email.trim(),
                password = form.password,
            ),
        )
    }
}
