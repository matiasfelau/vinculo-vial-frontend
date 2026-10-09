package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.AlertAttemptRequestDto
import ar.felaufernandezbottaro.vinculovial.data.alert.dto.AlertAttemptResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AlertAttemptApi {

    /**
     * Llamada 1 del flujo de alertas (US-08): Alert resuelve los dueños contra Vehicle y, si hay
     * al menos uno distinto del emisor, devuelve el `attempt_id` que ata las fotos de la
     * llamada 2. Protegido por JWT (Kong) + Rate Limiting por `X-User-Id` (10 req/min, 30 req/hora),
     * no por App Attestation -- `AttestationInterceptor` trata este path en modo best-effort.
     */
    @POST("api/v1/alerts/attempts")
    suspend fun startAttempt(
        @Header("Authorization") authorization: String,
        @Body body: AlertAttemptRequestDto,
    ): Response<AlertAttemptResponseDto>
}
