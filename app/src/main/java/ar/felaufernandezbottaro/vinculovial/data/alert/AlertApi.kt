package ar.felaufernandezbottaro.vinculovial.data.alert

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface AlertApi {

    /**
     * Llamada 2 del flujo de alertas (US-08): multipart con la parte `metadata` (JSON) y las
     * fotos cifradas para IA (`general`, `plate`, `detail` o `context`, como
     * `application/octet-stream`). Protegido por JWT (Kong) + Rate Limiting por `X-User-Id`, no
     * por App Attestation -- `AttestationInterceptor` trata este path en modo best-effort, igual
     * que `/alerts/attempts`.
     */
    @Multipart
    @POST("api/v1/alerts")
    suspend fun sendAlert(
        @Header("Authorization") authorization: String,
        @Part parts: List<MultipartBody.Part>,
    ): Response<Unit>
}
