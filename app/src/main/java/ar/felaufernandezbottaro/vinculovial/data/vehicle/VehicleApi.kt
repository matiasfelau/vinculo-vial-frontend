package ar.felaufernandezbottaro.vinculovial.data.vehicle

import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehicleCountDto
import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehicleRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Vehículos vinculados (US-14). Ninguna respuesta devuelve patentes: sólo `{ count, max }`.
 *
 * `/vehicles` es autenticado por recurso: a diferencia de `/signup`/`/signin`, ningún
 * interceptor agrega `Authorization` de entrada, así que se pasa explícito acá. Eso es también lo
 * que permite que `TokenAuthenticator` lo lea de vuelta del request original si Kong responde 401
 * y dispare el refresh silencioso.
 *
 * Protegido por JWT (Kong), no por App Attestation: `AttestationInterceptor` trata estos paths en
 * modo best-effort, igual que `/logout`.
 */
interface VehicleApi {

    @GET("api/v1/vehicles")
    suspend fun getCount(@Header("Authorization") authorization: String): Response<VehicleCountDto>

    @POST("api/v1/vehicles")
    suspend fun link(
        @Header("Authorization") authorization: String,
        @Body body: VehicleRequestDto,
    ): Response<VehicleCountDto>

    @POST("api/v1/vehicles/unlink")
    suspend fun unlink(
        @Header("Authorization") authorization: String,
        @Body body: VehicleRequestDto,
    ): Response<VehicleCountDto>

    /** Desvincula todos los vehículos del usuario: 204. */
    @DELETE("api/v1/vehicles")
    suspend fun unlinkAll(@Header("Authorization") authorization: String): Response<Unit>
}
