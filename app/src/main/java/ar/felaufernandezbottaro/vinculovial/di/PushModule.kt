package ar.felaufernandezbottaro.vinculovial.di

import android.content.Context
import android.content.SharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.notifications.PushTokenRepositoryImpl
import ar.felaufernandezbottaro.vinculovial.data.push.FcmTokenDeletionSource
import ar.felaufernandezbottaro.vinculovial.data.push.FcmTokenTaskSource
import ar.felaufernandezbottaro.vinculovial.data.push.FirebaseFcmTokenProvider
import ar.felaufernandezbottaro.vinculovial.domain.push.FcmTokenProvider
import ar.felaufernandezbottaro.vinculovial.domain.push.PushTokenRepository
import com.google.firebase.messaging.FirebaseMessaging
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

        /** Detrás de [FcmTokenTaskSource] para que un test pueda sustituirlo sin mockk. */
        @Provides
        @Singleton
        fun provideFcmTokenTaskSource(): FcmTokenTaskSource =
            FcmTokenTaskSource { FirebaseMessaging.getInstance().token }

        /** Detrás de [FcmTokenDeletionSource] por la misma razón (US-11, baja de cuenta). */
        @Provides
        @Singleton
        fun provideFcmTokenDeletionSource(): FcmTokenDeletionSource =
            FcmTokenDeletionSource { FirebaseMessaging.getInstance().deleteToken() }

        private const val PUSH_PREFS_FILE_NAME = "vinculo_vial_push_tokens"
    }
}
