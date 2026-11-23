package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.data.alert.AlertAttemptRepositoryImpl
import ar.felaufernandezbottaro.vinculovial.data.alert.AlertRepositoryImpl
import ar.felaufernandezbottaro.vinculovial.data.alert.VehiclePlateEncryptor
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertAttemptRepository
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertRepository
import ar.felaufernandezbottaro.vinculovial.domain.scan.PlateEncryptor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ar.felaufernandezbottaro.vinculovial.ExcludeFromCoverage

@ExcludeFromCoverage(reason = "Módulo de Hilt: solo cableado de dependencias, sin lógica propia")
@Module
@InstallIn(SingletonComponent::class)
abstract class AlertModule {

    @Binds
    @Singleton
    abstract fun bindAlertAttemptRepository(impl: AlertAttemptRepositoryImpl): AlertAttemptRepository

    @Binds
    @Singleton
    abstract fun bindAlertRepository(impl: AlertRepositoryImpl): AlertRepository

    @Binds
    @Singleton
    abstract fun bindPlateEncryptor(impl: VehiclePlateEncryptor): PlateEncryptor
}
