package ar.felaufernandezbottaro.vinculovial.di

import com.squareup.moshi.Moshi
import ar.felaufernandezbottaro.vinculovial.BuildConfig
import ar.felaufernandezbottaro.vinculovial.data.alert.AlertApi
import ar.felaufernandezbottaro.vinculovial.data.alert.VehicleCheckApi
import ar.felaufernandezbottaro.vinculovial.data.inbox.InboxApi
import ar.felaufernandezbottaro.vinculovial.data.notifications.NotificationsApi
import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import ar.felaufernandezbottaro.vinculovial.data.remote.AuthApiService
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

/**
 * Cliente de `/refresh` y `/logout`, sin [TokenAuthenticator] instalado -- por diseño, no por
 * un filtro de path: así es imposible que un 401 de estos dos endpoints dispare otro refresh,
 * y de paso se corta la dependencia circular (`TokenAuthenticator` → `SessionManager` →
 * `RefreshApi` → este cliente).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

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
        attestationInterceptor: AttestationInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(attestationInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .connectTimeout(BuildConfig.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(BuildConfig.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(BuildConfig.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    @Provides
    @Singleton
    @RefreshHttpClient
    fun provideRefreshOkHttpClient(
        attestationInterceptor: AttestationInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(attestationInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(BuildConfig.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(BuildConfig.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(BuildConfig.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
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

    /** Mismo cliente que `VehicleApi`: `/vehicles/check` es autenticado por JWT, no por attestation. */
    @Provides
    @Singleton
    fun provideVehicleCheckApi(retrofit: Retrofit): VehicleCheckApi =
        retrofit.create(VehicleCheckApi::class.java)

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

    /** Mismo cliente que `InboxApi`: `/notifications/tokens` es autenticado por JWT, no por attestation. */
    @Provides
    @Singleton
    fun provideNotificationsApi(retrofit: Retrofit): NotificationsApi =
        retrofit.create(NotificationsApi::class.java)

    @Provides
    @Singleton
    fun provideRefreshApi(@RefreshHttpClient retrofit: Retrofit): RefreshApi =
        retrofit.create(RefreshApi::class.java)
}
