package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.SendAlertRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AlertApi {

    /**
     * Protegido por JWT (Kong) + Rate Limiting por `X-User-Id`, no por App Attestation --
     * `AttestationInterceptor` trata este path en modo best-effort, igual que `/vehicles/check`.
     */
    @POST("api/v1/alerts")
    suspend fun sendAlert(
        @Header("Authorization") authorization: String,
        @Body body: SendAlertRequestDto,
    ): Response<Unit>
}
