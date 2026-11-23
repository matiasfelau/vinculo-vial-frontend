package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.data.vehicle.VehicleRepositoryImpl
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ar.felaufernandezbottaro.vinculovial.ExcludeFromCoverage

@ExcludeFromCoverage(reason = "Módulo de Hilt: solo cableado de dependencias, sin lógica propia")
@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleModule {

    @Binds
    @Singleton
    abstract fun bindVehicleRepository(impl: VehicleRepositoryImpl): VehicleRepository
}
