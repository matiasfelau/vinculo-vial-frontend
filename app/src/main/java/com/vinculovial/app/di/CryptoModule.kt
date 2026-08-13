package com.vinculovial.app.di

import com.vinculovial.app.BuildConfig
import com.vinculovial.app.data.crypto.AttestationTokenGenerator
import com.vinculovial.app.data.crypto.HpkeEncryptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    fun provideAttestationTokenGenerator(): AttestationTokenGenerator =
        AttestationTokenGenerator(
            psk = BuildConfig.APP_ATTESTATION_PSK,
            ttlSeconds = BuildConfig.ATTESTATION_TOKEN_TTL_SECONDS,
            packageName = BuildConfig.APPLICATION_ID,
        )
}
