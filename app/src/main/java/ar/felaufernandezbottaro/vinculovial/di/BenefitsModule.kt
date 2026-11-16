package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.data.benefits.BenefitsRepositoryImpl
import ar.felaufernandezbottaro.vinculovial.domain.benefits.BenefitsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BenefitsModule {

    @Binds
    @Singleton
    abstract fun bindBenefitsRepository(impl: BenefitsRepositoryImpl): BenefitsRepository
}
