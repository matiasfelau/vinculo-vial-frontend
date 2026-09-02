package ar.felaufernandezbottaro.vinculovial.data.inbox

import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.InboxPageDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
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
}
