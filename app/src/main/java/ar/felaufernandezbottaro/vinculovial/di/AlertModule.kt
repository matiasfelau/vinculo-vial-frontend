package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.data.alert.VehicleCheckRepositoryImpl
import ar.felaufernandezbottaro.vinculovial.domain.scan.VehicleCheckRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AlertModule {

    @Binds
    @Singleton
    abstract fun bindVehicleCheckRepository(impl: VehicleCheckRepositoryImpl): VehicleCheckRepository
}
