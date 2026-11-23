package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.data.signin.SignInRepositoryImpl
import ar.felaufernandezbottaro.vinculovial.data.signup.SignUpRepositoryImpl
import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInRepository
import ar.felaufernandezbottaro.vinculovial.domain.signup.SignUpRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ar.felaufernandezbottaro.vinculovial.ExcludeFromCoverage

@ExcludeFromCoverage(reason = "Módulo de Hilt: solo cableado de dependencias, sin lógica propia")
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSignUpRepository(impl: SignUpRepositoryImpl): SignUpRepository

    @Binds
    @Singleton
    abstract fun bindSignInRepository(impl: SignInRepositoryImpl): SignInRepository
}
