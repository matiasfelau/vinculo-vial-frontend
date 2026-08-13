package com.vinculovial.app.di

import com.vinculovial.app.BuildConfig
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
}
