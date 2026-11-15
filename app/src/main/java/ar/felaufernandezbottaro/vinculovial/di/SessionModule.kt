package ar.felaufernandezbottaro.vinculovial.di

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import ar.felaufernandezbottaro.vinculovial.BuildConfig
import ar.felaufernandezbottaro.vinculovial.data.session.AndroidBiometricCapability
import ar.felaufernandezbottaro.vinculovial.data.session.AndroidLegacySessionStorage
import ar.felaufernandezbottaro.vinculovial.data.session.BiometricCapability
import ar.felaufernandezbottaro.vinculovial.data.session.KeystoreSessionKeyVault
import ar.felaufernandezbottaro.vinculovial.data.session.LegacySessionStorage
import ar.felaufernandezbottaro.vinculovial.data.session.MonotonicClock
import ar.felaufernandezbottaro.vinculovial.data.session.SessionKeyVault
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Segundos en segundo plano a partir de los cuales volver a primer plano bloquea la sesión. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionLockAfterBackgroundSeconds

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    /** Donde vive el blob (refresh token envuelto). Es lo único que la app persiste de la sesión. */
    @Provides
    @Singleton
    fun provideSessionPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(SESSION_PREFS_FILE_NAME, Context.MODE_PRIVATE)

    /** Detrás de [SessionKeyVault] para probar la sesión sin un Keystore real. */
    @Provides
    @Singleton
    fun provideSessionKeyVault(): SessionKeyVault = KeystoreSessionKeyVault()

    @Provides
    @Singleton
    fun provideBiometricCapability(@ApplicationContext context: Context): BiometricCapability =
        AndroidBiometricCapability(context)

    @Provides
    @Singleton
    fun provideLegacySessionStorage(@ApplicationContext context: Context): LegacySessionStorage =
        AndroidLegacySessionStorage(context)

    @Provides
    @Singleton
    fun provideMonotonicClock(): MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }

    @Provides
    @SessionLockAfterBackgroundSeconds
    fun provideSessionLockAfterBackgroundSeconds(): Long = BuildConfig.SESSION_LOCK_AFTER_BACKGROUND_SECONDS

    /**
     * Archivo nuevo, distinto del de los tokens cifrados con Tink de antes de US-12 (esos se
     * borran en `LegacySessionMigration`).
     */
    private const val SESSION_PREFS_FILE_NAME = "vinculo_vial_session_unlock"
}
