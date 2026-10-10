package ar.felaufernandezbottaro.vinculovial.di

import android.content.Context
import ar.felaufernandezbottaro.vinculovial.data.inbox.SharedPreferencesReadAlertsStore
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReadAlertsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReadAlertsModule {

    /** Su propio archivo de preferencias: no comparte el de la sesión (`SessionModule`). */
    @Provides
    @Singleton
    fun provideReadAlertsStore(@ApplicationContext context: Context): ReadAlertsStore =
        SharedPreferencesReadAlertsStore(context.getSharedPreferences(READ_ALERTS_PREFS_FILE_NAME, Context.MODE_PRIVATE))

    private const val READ_ALERTS_PREFS_FILE_NAME = "vinculo_vial_read_alerts"
}
