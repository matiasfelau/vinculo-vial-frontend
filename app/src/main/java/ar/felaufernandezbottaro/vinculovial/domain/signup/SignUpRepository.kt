package ar.felaufernandezbottaro.vinculovial.domain.signup

import ar.felaufernandezbottaro.vinculovial.domain.signup.model.Credentials

/**
 * Puerto de salida del registro. La implementación (cifrado HPKE + HTTP) vive en `data`.
 */
interface SignUpRepository {
    suspend fun signUp(credentials: Credentials): SignUpResult
}
