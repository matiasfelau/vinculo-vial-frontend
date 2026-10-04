package ar.felaufernandezbottaro.vinculovial.data.remote

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST

/**
 * Fixture permanente de `CircuitBreakerInterceptorTest`: una interfaz Retrofit real, usada a
 * través de Retrofit de verdad, así el tag `Invocation` que lee el breaker lo adjunta la
 * propia librería y no un test armándolo a mano.
 *
 * Reproduce a propósito los dos casos que una clave por ruta confundiría y que existen en
 * la API real: métodos que comparten prefijo (`/alerts` y `/alerts/inbox`) y métodos con la
 * misma ruta y distinto verbo (`POST` y `DELETE` sobre `/notifications/tokens`).
 */
internal interface BreakerFixtureApi {

    @POST("api/v1/alerts")
    fun postAlert(): Call<Unit>

    @GET("api/v1/alerts/inbox")
    fun getInbox(): Call<Unit>

    @POST("api/v1/notifications/tokens")
    fun registerToken(): Call<Unit>

    @HTTP(method = "DELETE", path = "api/v1/notifications/tokens")
    fun deleteToken(): Call<Unit>
}
