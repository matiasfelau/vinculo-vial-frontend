package ar.felaufernandezbottaro.vinculovial.data.remote.auth

import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshResponseDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshTokenRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Endpoints de sesión de US-03 (RTR y Logout). Separados de `AuthApiService` (US-01/US-02)
 * porque comparten una restricción propia: quedan excluidos del Authenticator que atrapa
 * los 401 del resto de la API, para no entrar en recursión infinita al refrescar.
 */
interface RefreshApi {

    /**
     * El header `X-App-Attestation` lo agrega el interceptor de attestation y acá es
     * obligatorio: el Gateway lo exige para dejar pasar el refresh.
     */
    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshTokenRequestDto): Response<RefreshResponseDto>

    /**
     * A diferencia de `/refresh`, acá el header de attestation es best-effort: el interceptor
     * lo agrega si puede generarlo, pero nunca bloquea la llamada si falla -- el usuario tiene
     * que poder cerrar sesión aunque la attestation esté rota. El backend siempre responde
     * `200`, incluso si el refresh token no existía o ya estaba revocado (anti-enumeración).
     */
    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: RefreshTokenRequestDto): Response<Unit>
}
