package ar.felaufernandezbottaro.vinculovial.domain.signin

import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInCredentials

/**
 * Puerto de salida del login. La implementación (cifrado HPKE + HTTP + persistencia del
 * par de tokens) vive en `data`.
 */
interface SignInRepository {
    suspend fun signIn(credentials: SignInCredentials): SignInResult
}
