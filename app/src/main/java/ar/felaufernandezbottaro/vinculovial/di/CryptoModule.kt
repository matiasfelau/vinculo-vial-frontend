package ar.felaufernandezbottaro.vinculovial.di

import android.content.Context
import ar.felaufernandezbottaro.vinculovial.BuildConfig
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationMode
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationTokenGenerator
import ar.felaufernandezbottaro.vinculovial.data.crypto.MockAttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.PlayIntegrityAttestationProvider
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import ar.felaufernandezbottaro.vinculovial.ExcludeFromCoverage

/** Public Key HPKE de `vinculo-vial-identity`, usada por `/signup` y `/signin`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IdentityHpkePublicKey

/** Public Key HPKE de `vinculo-vial-vehicle` (US-04): cifra el `payload_enc` de `/vehicles`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VehicleHpkePublicKey

/** Public Key HPKE de `vinculo-vial-ia` (US-08): cifra las fotos de evidencia de las alertas. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IaHpkePublicKey

@ExcludeFromCoverage(reason = "Módulo de Hilt: solo cableado de dependencias, sin lógica propia")
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
    @IaHpkePublicKey
    fun provideIaHpkePublicKey(): String = BuildConfig.IA_HPKE_PUBLIC_KEY

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
     * `IntegrityManagerFactory.createStandard` queda detrás de su propio `@Provides`, en vez
     * de llamarse directo dentro de `PlayIntegrityAttestationProvider`: así ese constructor
     * recibe la interfaz del SDK ([StandardIntegrityManager]) en vez de un `Context`, y un
     * test puede reemplazarla por un fake de mano sin mockk ni Robolectric.
     */
    @Provides
    @Singleton
    fun provideStandardIntegrityManager(@ApplicationContext context: Context): StandardIntegrityManager =
        IntegrityManagerFactory.createStandard(context)

    /**
     * El modo se elige en tiempo de build. El Gateway tiene que validar de la misma
     * forma: mock = HMAC con la PSK compartida; play_integrity = decodificación del
     * token contra la Play Integrity API de Google.
     */
    @Provides
    @Singleton
    fun provideAttestationProvider(
        mode: AttestationMode,
        integrityManager: Lazy<StandardIntegrityManager>,
        generator: Lazy<AttestationTokenGenerator>,
    ): AttestationProvider = when (mode) {
        AttestationMode.MOCK -> MockAttestationProvider(generator.get())
        AttestationMode.PLAY_INTEGRITY -> PlayIntegrityAttestationProvider(
            integrityManager = integrityManager.get(),
            cloudProjectNumber = BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER,
        )
    }
}
