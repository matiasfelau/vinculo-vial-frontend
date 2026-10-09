package ar.felaufernandezbottaro.vinculovial.data.inbox

import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.FeedbackRequestDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.InboxPageDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.ReportRequestDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface InboxApi {

    /**
     * Protegido por JWT (Kong) + Rate Limiting por `X-User-Id` (20 req/min). El `limit` nunca
     * se envía: el servidor aplica su default. [cursor] es el valor opaco de la página
     * anterior, ausente en la primera carga -- la app nunca lo interpreta ni lo construye.
     */
    @GET("api/v1/alerts/inbox")
    suspend fun getInbox(
        @Header("Authorization") authorization: String,
        @Query("cursor") cursor: String? = null,
    ): Response<InboxPageDto>

    /**
     * La foto redactada como `image/jpeg`, `Cache-Control: no-store`. Se pide como [ResponseBody]
     * (sin `@Streaming`, así que Retrofit la lee entera a memoria): la app la decodifica en
     * memoria y nunca la escribe a disco. Los segmentos de la ruta se codifican como path.
     */
    @GET("api/v1/alerts/{alertId}/evidence/{role}/{index}")
    suspend fun getEvidence(
        @Header("Authorization") authorization: String,
        @Path("alertId") alertId: String,
        @Path("role") role: String,
        @Path("index") index: Int,
    ): Response<ResponseBody>

    /** Upsert de la valoración; responde 204. */
    @PUT("api/v1/alerts/{alertId}/feedback")
    suspend fun putFeedback(
        @Header("Authorization") authorization: String,
        @Path("alertId") alertId: String,
        @Body body: FeedbackRequestDto,
    ): Response<Unit>

    /** Reporta al emisor; responde 202, o 409 si ya se había reportado. Rate limit propio de 5/min, 20/h. */
    @POST("api/v1/alerts/{alertId}/report")
    suspend fun reportSender(
        @Header("Authorization") authorization: String,
        @Path("alertId") alertId: String,
        @Body body: ReportRequestDto,
    ): Response<Unit>
}
