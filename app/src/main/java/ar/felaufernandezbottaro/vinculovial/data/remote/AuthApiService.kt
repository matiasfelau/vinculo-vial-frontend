package ar.felaufernandezbottaro.vinculovial.data.remote

import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.HpkeEnvelopeDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.ResetTokenResponseDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.VerificationTokenResponseDto
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

    /** US-13, alta: pide el código de verificación del email. 202 exista o no una cuenta. */
    @POST("api/v1/auth/email-verifications")
    suspend fun requestEmailVerification(@Body body: HpkeEnvelopeDto): Response<Unit>

    /** US-13, alta: confirma el código y devuelve el `verification_token` que consume el signup. */
    @POST("api/v1/auth/email-verifications/confirm")
    suspend fun confirmEmailVerification(@Body body: HpkeEnvelopeDto): Response<VerificationTokenResponseDto>

    /** US-13, recuperación: pide el código por DNI. 202 exista o no la cuenta. */
    @POST("api/v1/auth/password-resets")
    suspend fun requestPasswordReset(@Body body: HpkeEnvelopeDto): Response<Unit>

    /** US-13, recuperación: confirma el código y devuelve el `reset_token`. */
    @POST("api/v1/auth/password-resets/confirm")
    suspend fun confirmPasswordReset(@Body body: HpkeEnvelopeDto): Response<ResetTokenResponseDto>

    /** US-13, recuperación: cambia la contraseña (204). Exige DNI, trámite, sexo y selfie. */
    @POST("api/v1/auth/password-resets/complete")
    suspend fun completePasswordReset(@Body body: HpkeEnvelopeDto): Response<Unit>
}
