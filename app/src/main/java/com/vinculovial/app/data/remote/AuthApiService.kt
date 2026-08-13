package com.vinculovial.app.data.remote

import com.vinculovial.app.data.signup.dto.SignUpRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {

    /**
     * Registro síncrono. Devuelve `Response<Unit>` porque lo que importa es el código:
     * 201 / 400 / 403 / 409. El cuerpo de error no se usa (mensajes genéricos en UI).
     *
     * El header `X-App-Attestation` lo agrega [AttestationInterceptor].
     */
    @POST("api/v1/auth/signup")
    suspend fun signUp(@Body body: SignUpRequestDto): Response<Unit>
}
