package ar.felaufernandezbottaro.vinculovial.di

import android.content.Context
import ar.felaufernandezbottaro.vinculovial.BuildConfig
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationMode
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationTokenGenerator
import ar.felaufernandezbottaro.vinculovial.data.crypto.MockAttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.PlayIntegrityAttestationProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Public Key HPKE de `vinculo-vial-identity`: sólo puede leer `dni_enc`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IdentityHpkePublicKey

/** Public Key HPKE de `vinculo-vial-vehicle` (US-04): sólo puede leer `pat_enc`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VehicleHpkePublicKey

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    // HpkeEncryptor ya no se provee acá: tiene @Inject constructor() propio (sin clave
    // fija) desde que se parametriza por clave pública de destino en cada llamada (US-04).
    // Esas claves se inyectan a cada repositorio como String calificado (ver abajo) en vez
    // de leerse de BuildConfig directamente ahí, para que los tests puedan seguir
    // sustituyéndolas por un par de prueba -- mismo criterio que provideAttestationTokenGenerator.

    @Provides
    @Singleton
    @IdentityHpkePublicKey
    fun provideIdentityHpkePublicKey(): String = BuildConfig.IDENTITY_HPKE_PUBLIC_KEY

    @Provides
    @Singleton
    @VehicleHpkePublicKey
    fun provideVehicleHpkePublicKey(): String = BuildConfig.VEHICLE_HPKE_PUBLIC_KEY

    @Provides
    @Singleton
    fun provideAttestationMode(): AttestationMode = AttestationMode.from(BuildConfig.ATTESTATION_MODE)

    @Provides
    @Singleton
    fun provideAttestationTokenGenerator(): AttestationTokenGenerator =
        AttestationTokenGenerator(
            psk = BuildConfig.APP_ATTESTATION_PSK,
            ttlSeconds = BuildConfig.ATTESTATION_TOKEN_TTL_SECONDS,
            packageName = BuildConfig.APPLICATION_ID,
        )

    /**
     * El modo se elige en tiempo de build. El Gateway tiene que validar de la misma
     * forma: mock = HMAC con la PSK compartida; play_integrity = decodificación del
     * token contra la Play Integrity API de Google.
     */
    @Provides
    @Singleton
    fun provideAttestationProvider(
        mode: AttestationMode,
        @ApplicationContext context: Context,
        generator: dagger.Lazy<AttestationTokenGenerator>,
    ): AttestationProvider = when (mode) {
        AttestationMode.MOCK -> MockAttestationProvider(generator.get())
        AttestationMode.PLAY_INTEGRITY -> PlayIntegrityAttestationProvider(
            context = context,
            cloudProjectNumber = BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER,
        )
    }
}
