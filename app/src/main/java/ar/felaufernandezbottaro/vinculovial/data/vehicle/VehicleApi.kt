package ar.felaufernandezbottaro.vinculovial.data.vehicle

import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.LinkVehicleRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface VehicleApi {

    /**
     * `/vehicles` es el primer endpoint autenticado por recurso de la app: a diferencia de
     * `/signup`/`/signin`, ningún interceptor agrega `Authorization` de entrada, así que se
     * pasa explícito acá. Eso es también lo que permite que `TokenAuthenticator` lo lea de
     * vuelta del request original si Kong responde 401 y dispare el refresh silencioso.
     *
     * Protegido por JWT (Kong), no por App Attestation -- `AttestationInterceptor` trata
     * este path en modo best-effort, igual que `/logout`.
     */
    @POST("api/v1/vehicles")
    suspend fun linkVehicle(
        @Header("Authorization") authorization: String,
        @Body body: LinkVehicleRequestDto,
    ): Response<Unit>
}
