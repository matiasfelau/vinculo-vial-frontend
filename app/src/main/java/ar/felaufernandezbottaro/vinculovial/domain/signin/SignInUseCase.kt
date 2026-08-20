package ar.felaufernandezbottaro.vinculovial.domain.signin

import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInCredentials
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInForm
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInRules
import javax.inject.Inject

/**
 * Orquesta US-02: última validación Fail-Fast del lado del dominio (la UI ya validó, pero
 * el dominio no confía en ella) y delega el resto — cifrado HPKE, attestation, POST,
 * persistencia del par de tokens — en [SignInRepository].
 */
class SignInUseCase @Inject constructor(
    private val repository: SignInRepository,
) {

    suspend operator fun invoke(form: SignInForm): SignInResult {
        if (SignInRules.problems(form).isNotEmpty()) {
            return SignInResult.Failed(SignInError.InvalidData)
        }

        return repository.signIn(SignInCredentials(dni = form.dni.trim(), password = form.password))
    }
}
