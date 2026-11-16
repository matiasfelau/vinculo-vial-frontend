package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.data.account.AccountRepositoryImpl
import ar.felaufernandezbottaro.vinculovial.data.account.LocalAccountDataEraserImpl
import ar.felaufernandezbottaro.vinculovial.domain.account.AccountRepository
import ar.felaufernandezbottaro.vinculovial.domain.account.LocalAccountDataEraser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ar.felaufernandezbottaro.vinculovial.ExcludeFromCoverage

@ExcludeFromCoverage(reason = "Módulo de Hilt: solo cableado de dependencias, sin lógica propia")
@Module
@InstallIn(SingletonComponent::class)
abstract class AccountModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindLocalAccountDataEraser(impl: LocalAccountDataEraserImpl): LocalAccountDataEraser
}
