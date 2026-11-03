package ar.felaufernandezbottaro.vinculovial.data.account

import ar.felaufernandezbottaro.vinculovial.data.account.dto.DeleteAccountRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.Header

interface AccountApi {

    // Retrofit no permite @Body en @DELETE; @HTTP con hasBody = true es la forma de mandarlo
    // (mismo criterio que `NotificationsApi.deleteToken`).
    @HTTP(method = "DELETE", path = "api/v1/users/me", hasBody = true)
    suspend fun deleteAccount(
        @Header("Authorization") authorization: String,
        @Body body: DeleteAccountRequestDto,
    ): Response<Unit>
}
