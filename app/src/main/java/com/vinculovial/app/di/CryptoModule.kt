package com.vinculovial.app.di

import android.content.Context
import com.vinculovial.app.BuildConfig
import com.vinculovial.app.data.crypto.AttestationMode
import com.vinculovial.app.data.crypto.AttestationProvider
import com.vinculovial.app.data.crypto.AttestationTokenGenerator
import com.vinculovial.app.data.crypto.HpkeEncryptor
import com.vinculovial.app.data.crypto.MockAttestationProvider
import com.vinculovial.app.data.crypto.PlayIntegrityAttestationProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideHpkeEncryptor(): HpkeEncryptor =
        HpkeEncryptor(identityPublicKeyBase64 = BuildConfig.IDENTITY_HPKE_PUBLIC_KEY)

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
