package ar.felaufernandezbottaro.vinculovial.data.remote

import ar.felaufernandezbottaro.vinculovial.data.signin.dto.SignInRequestDto
import ar.felaufernandezbottaro.vinculovial.data.signin.dto.SignInResponseDto
import ar.felaufernandezbottaro.vinculovial.data.signup.dto.SignUpRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {

    /**
     * Registro síncrono. Devuelve `Response<Unit>` porque el cuerpo de éxito no trae nada
     * que deserializar: lo que importa es el código, 201 / 400 / 403 / 409. El de error sí
     * se lee — ver [SignUpRepositoryImpl] — pero sólo para diagnóstico en Logcat; el mensaje
     * que ve el usuario sigue siendo genérico en los dos casos.
     *
     * El header `X-App-Attestation` lo agrega [AttestationInterceptor].
     */
    @POST("api/v1/auth/signup")
    suspend fun signUp(@Body body: SignUpRequestDto): Response<Unit>

    /**
     * Login síncrono. A diferencia de `signUp`, el 200 sí trae cuerpo: el par de tokens de
     * sesión que persiste `SecureTokenStore`.
     *
     * El header `X-App-Attestation` lo agrega [AttestationInterceptor].
     */
    @POST("api/v1/auth/signin")
    suspend fun signIn(@Body body: SignInRequestDto): Response<SignInResponseDto>
}
