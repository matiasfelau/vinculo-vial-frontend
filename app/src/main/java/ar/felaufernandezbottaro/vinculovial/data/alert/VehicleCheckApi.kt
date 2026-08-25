package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.VehicleCheckRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface VehicleCheckApi {

    /**
     * Protegido por JWT (Kong) + Rate Limiting por `X-User-Id` (5 req/min, 10 req/hora), no
     * por App Attestation -- `AttestationInterceptor` trata este path en modo best-effort,
     * igual que `/vehicles`.
     */
    @POST("api/v1/vehicles/check")
    suspend fun checkVehicle(
        @Header("Authorization") authorization: String,
        @Body body: VehicleCheckRequestDto,
    ): Response<Unit>
}
