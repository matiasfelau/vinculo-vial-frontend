package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.data.evidence.AndroidEvidenceRasterFactory
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceRasterFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ar.felaufernandezbottaro.vinculovial.ExcludeFromCoverage

@ExcludeFromCoverage(reason = "Módulo de Hilt: solo cableado de dependencias, sin lógica propia")
@Module
@InstallIn(SingletonComponent::class)
abstract class EvidenceModule {

    @Binds
    @Singleton
    abstract fun bindEvidenceRasterFactory(impl: AndroidEvidenceRasterFactory): EvidenceRasterFactory
}
