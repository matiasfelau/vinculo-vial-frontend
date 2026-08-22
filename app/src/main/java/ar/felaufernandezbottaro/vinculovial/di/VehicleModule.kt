package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.data.scanner.MlKitQrCedulaScanner
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.QrCedulaScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleModule {

    @Binds
    @Singleton
    abstract fun bindQrCedulaScanner(impl: MlKitQrCedulaScanner): QrCedulaScanner
}
