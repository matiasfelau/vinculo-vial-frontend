package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.BuildConfig
import ar.felaufernandezbottaro.vinculovial.data.terms.RawTermsSource
import ar.felaufernandezbottaro.vinculovial.domain.terms.LegalInfo
import ar.felaufernandezbottaro.vinculovial.domain.terms.TermsSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TermsModule {

    @Binds
    @Singleton
    abstract fun bindTermsSource(impl: RawTermsSource): TermsSource

    companion object {

        /** Los datos del responsable y la versión de los Términos, de `BuildConfig` (ver `local.properties.example`). */
        @Provides
        @Singleton
        fun provideLegalInfo(): LegalInfo = LegalInfo(
            responsibleName = BuildConfig.LEGAL_RESPONSIBLE_NAME,
            contactEmail = BuildConfig.LEGAL_CONTACT_EMAIL,
            address = BuildConfig.LEGAL_ADDRESS,
            version = BuildConfig.TERMS_VERSION,
        )
    }
}
