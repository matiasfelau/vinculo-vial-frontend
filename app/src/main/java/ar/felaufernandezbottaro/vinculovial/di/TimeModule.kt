package ar.felaufernandezbottaro.vinculovial.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    /** Reloj inyectado para que el vencimiento del intento se pueda probar sin esperar. */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
