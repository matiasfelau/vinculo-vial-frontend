package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.data.scan.MLKitRecognitionStrategy
import ar.felaufernandezbottaro.vinculovial.domain.scan.PlateRecognitionStrategy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {

    @Binds
    @Singleton
    abstract fun bindPlateRecognitionStrategy(impl: MLKitRecognitionStrategy): PlateRecognitionStrategy
}
