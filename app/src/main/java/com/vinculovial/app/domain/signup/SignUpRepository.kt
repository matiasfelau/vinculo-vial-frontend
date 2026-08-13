package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.Credentials

/**
 * Puerto de salida del registro. La implementación (cifrado HPKE + HTTP) vive en `data`.
 */
interface SignUpRepository {
    suspend fun signUp(credentials: Credentials): SignUpResult
}
