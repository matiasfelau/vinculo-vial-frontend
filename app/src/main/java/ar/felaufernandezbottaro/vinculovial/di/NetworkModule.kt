package ar.felaufernandezbottaro.vinculovial.di

import com.squareup.moshi.Moshi
import ar.felaufernandezbottaro.vinculovial.BuildConfig
import ar.felaufernandezbottaro.vinculovial.data.account.AccountApi
import ar.felaufernandezbottaro.vinculovial.data.alert.AlertApi
import ar.felaufernandezbottaro.vinculovial.data.alert.AlertAttemptApi
import ar.felaufernandezbottaro.vinculovial.data.benefits.BenefitsApi
import ar.felaufernandezbottaro.vinculovial.data.inbox.InboxApi
import ar.felaufernandezbottaro.vinculovial.data.notifications.NotificationsApi
import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import ar.felaufernandezbottaro.vinculovial.data.remote.AuthApiService
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerConfig
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerInterceptor
import ar.felaufernandezbottaro.vinculovial.data.remote.NetworkTimeouts
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.RefreshApi
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.TokenAuthenticator
import ar.felaufernandezbottaro.vinculovial.data.vehicle.VehicleApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import ar.felaufernandezbottaro.vinculovial.ExcludeFromCoverage

/**
 * Cliente de `/refresh` y `/logout`, sin [TokenAuthenticator] instalado -- por diseño, no por
 * un filtro de path: así es imposible que un 401 de estos dos endpoints dispare otro refresh,
 * y de paso se corta la dependencia circular (`TokenAuthenticator` → `SessionManager` →
 * `RefreshApi` → este cliente).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshHttpClient

@ExcludeFromCoverage(reason = "Módulo de Hilt: solo cableado de dependencias, sin lógica propia")
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideNetworkTimeouts(): NetworkTimeouts = NetworkTimeouts(
        connectMs = BuildConfig.NETWORK_TIMEOUT_MS,
        readMs = BuildConfig.NETWORK_TIMEOUT_MS,
        writeMs = BuildConfig.NETWORK_TIMEOUT_MS,
        attestationMs = BuildConfig.ATTESTATION_TIMEOUT_MS,
    )

    /**
     * Timeouts y política de reintento de los dos clientes, en un solo lugar: que uno de los
     * dos quede sin alguno de estos límites es el tipo de regresión que no se ve hasta que un
     * upstream se cuelga en producción.
     *
     * `connect`/`read`/`write` sólo miden el socket. `AttestationInterceptor` hace su trabajo
     * *antes* de abrir la conexión, así que sin `callTimeout` -- que acota la llamada completa,
     * interceptors incluidos, y que en OkHttp vale 0 (sin límite) por defecto -- un Task de
     * Play que no resuelve dejaba la request colgada sin límite. Por eso `callTimeout` usa
     * [NetworkTimeouts.callMs] (attestation + connect + read), no el mismo valor que `read`.
     *
     * `retryOnConnectionFailure(false)`: la app nunca reintenta una llamada saliente por su
     * cuenta (ver el contrato de resiliencia).
     */
    private fun OkHttpClient.Builder.withTimeouts(timeouts: NetworkTimeouts): OkHttpClient.Builder = this
        .connectTimeout(timeouts.connectMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeouts.readMs, TimeUnit.MILLISECONDS)
        .writeTimeout(timeouts.writeMs, TimeUnit.MILLISECONDS)
        .callTimeout(timeouts.callMs, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideCircuitBreakerConfig(): CircuitBreakerConfig = CircuitBreakerConfig(
        failureThreshold = BuildConfig.CIRCUIT_BREAKER_FAILURE_THRESHOLD,
        windowMs = BuildConfig.CIRCUIT_BREAKER_WINDOW_MS,
        openMs = BuildConfig.CIRCUIT_BREAKER_OPEN_MS,
    )

    /**
     * Nivel BASIC a propósito: método, URL y código de estado. Nunca el cuerpo, que
     * contiene el ciphertext, ni los headers, que llevan la attestation.
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    /** El único que lleva [TokenAuthenticator]: `AuthApiService` y cualquier endpoint autenticado futuro. */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        timeouts: NetworkTimeouts,
        circuitBreakerInterceptor: CircuitBreakerInterceptor,
        attestationInterceptor: AttestationInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        // Antes que la attestation: con el circuito abierto, tampoco se paga el costo de
        // Play Integrity -- ver CircuitBreakerInterceptor.
        .addInterceptor(circuitBreakerInterceptor)
        .addInterceptor(attestationInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .withTimeouts(timeouts)
        .build()

    @Provides
    @Singleton
    @RefreshHttpClient
    fun provideRefreshOkHttpClient(
        timeouts: NetworkTimeouts,
        circuitBreakerInterceptor: CircuitBreakerInterceptor,
        attestationInterceptor: AttestationInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(circuitBreakerInterceptor)
        .addInterceptor(attestationInterceptor)
        .addInterceptor(loggingInterceptor)
        .withTimeouts(timeouts)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    @RefreshHttpClient
    fun provideRefreshRetrofit(@RefreshHttpClient client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService =
        retrofit.create(AuthApiService::class.java)

    /**
     * Mismo cliente que `AuthApiService`, con `TokenAuthenticator` instalado: el 401 en
     * `/vehicles` dispara el refresh automático sin trabajo extra en este repositorio.
     */
    @Provides
    @Singleton
    fun provideVehicleApi(retrofit: Retrofit): VehicleApi =
        retrofit.create(VehicleApi::class.java)

    /** Mismo cliente que `VehicleApi`: `/alerts/attempts` es autenticado por JWT, no por attestation. */
    @Provides
    @Singleton
    fun provideAlertAttemptApi(retrofit: Retrofit): AlertAttemptApi =
        retrofit.create(AlertAttemptApi::class.java)

    /** Mismo cliente que `VehicleApi`: `/alerts` es autenticado por JWT, no por attestation. */
    @Provides
    @Singleton
    fun provideAlertApi(retrofit: Retrofit): AlertApi =
        retrofit.create(AlertApi::class.java)

    /** Mismo cliente que `AlertApi`: `/alerts/inbox` es autenticado por JWT, no por attestation. */
    @Provides
    @Singleton
    fun provideInboxApi(retrofit: Retrofit): InboxApi =
        retrofit.create(InboxApi::class.java)

    /** Mismo cliente que `VehicleApi`: `/users/me` (baja de cuenta) es autenticado por JWT, no por attestation. */
    @Provides
    @Singleton
    fun provideAccountApi(retrofit: Retrofit): AccountApi =
        retrofit.create(AccountApi::class.java)

    /** Mismo cliente que `InboxApi`: `/notifications/tokens` es autenticado por JWT, no por attestation. */
    @Provides
    @Singleton
    fun provideNotificationsApi(retrofit: Retrofit): NotificationsApi =
        retrofit.create(NotificationsApi::class.java)

    /** Mismo cliente que `InboxApi`: `/benefits` es autenticado por JWT, no por attestation. */
    @Provides
    @Singleton
    fun provideBenefitsApi(retrofit: Retrofit): BenefitsApi =
        retrofit.create(BenefitsApi::class.java)

    @Provides
    @Singleton
    fun provideRefreshApi(@RefreshHttpClient retrofit: Retrofit): RefreshApi =
        retrofit.create(RefreshApi::class.java)
}
