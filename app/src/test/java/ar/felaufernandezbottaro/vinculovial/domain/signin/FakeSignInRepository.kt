package ar.felaufernandezbottaro.vinculovial.domain.signin

import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInCredentials

/** Repositorio falso: registra las credenciales recibidas y devuelve lo programado. */
class FakeSignInRepository(
    var result: SignInResult = SignInResult.SignedIn,
) : SignInRepository {

    val received = mutableListOf<SignInCredentials>()

    override suspend fun signIn(credentials: SignInCredentials): SignInResult {
        received += credentials
        return result
    }
}
