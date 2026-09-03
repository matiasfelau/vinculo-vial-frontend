package ar.felaufernandezbottaro.vinculovial.di

import android.content.Context
import android.content.SharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.notifications.PushTokenRepositoryImpl
import ar.felaufernandezbottaro.vinculovial.data.push.FirebaseFcmTokenProvider
import ar.felaufernandezbottaro.vinculovial.domain.push.FcmTokenProvider
import ar.felaufernandezbottaro.vinculovial.domain.push.PushTokenRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PushPreferences

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {

    @Binds
    @Singleton
    abstract fun bindPushTokenRepository(impl: PushTokenRepositoryImpl): PushTokenRepository

    @Binds
    @Singleton
    abstract fun bindFcmTokenProvider(impl: FirebaseFcmTokenProvider): FcmTokenProvider

    companion object {
        @Provides
        @Singleton
        @PushPreferences
        fun providePushPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences(PUSH_PREFS_FILE_NAME, Context.MODE_PRIVATE)

        private const val PUSH_PREFS_FILE_NAME = "vinculo_vial_push_tokens"
    }
}
