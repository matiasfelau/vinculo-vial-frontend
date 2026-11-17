package ar.felaufernandezbottaro.vinculovial.data.benefits

import ar.felaufernandezbottaro.vinculovial.data.benefits.dto.BenefitDto
import ar.felaufernandezbottaro.vinculovial.data.benefits.dto.ClaimResponseDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Beneficios de partners (US-18), detrás de Kong con JWT y Rate Limiting por `X-User-Id`: 60/min
 * la lista, 120/min los banners y 10/min y 30/h el `claim`.
 */
interface BenefitsApi {

    @GET("api/v1/benefits")
    suspend fun getBenefits(
        @Header("Authorization") authorization: String,
    ): Response<List<BenefitDto>>

    /**
     * El banner como bytes, `Cache-Control: private, max-age=3600`. Con [ifNoneMatch] igual al
     * `ETag` guardado, el servidor responde 304 sin cuerpo.
     */
    @GET("api/v1/benefits/{id}/banner")
    suspend fun getBanner(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Header("If-None-Match") ifNoneMatch: String? = null,
    ): Response<ResponseBody>

    /** Idempotente: si el usuario ya tenía código en la campaña, devuelve el mismo. */
    @POST("api/v1/benefits/{id}/claim")
    suspend fun claim(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    ): Response<ClaimResponseDto>
}
