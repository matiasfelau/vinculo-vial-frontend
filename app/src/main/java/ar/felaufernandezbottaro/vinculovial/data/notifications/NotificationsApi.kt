package ar.felaufernandezbottaro.vinculovial.data.notifications

import ar.felaufernandezbottaro.vinculovial.data.notifications.dto.FcmTokenRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST

interface NotificationsApi {

    @POST("api/v1/notifications/tokens")
    suspend fun registerToken(
        @Header("Authorization") authorization: String,
        @Body body: FcmTokenRequestDto,
    ): Response<Unit>

    // Retrofit no permite @Body en @DELETE (sólo POST/PUT/PATCH lo aceptan de forma directa);
    // @HTTP con hasBody = true es la forma correcta de mandar cuerpo en un DELETE.
    @HTTP(method = "DELETE", path = "api/v1/notifications/tokens", hasBody = true)
    suspend fun deleteToken(
        @Header("Authorization") authorization: String,
        @Body body: FcmTokenRequestDto,
    ): Response<Unit>
}
