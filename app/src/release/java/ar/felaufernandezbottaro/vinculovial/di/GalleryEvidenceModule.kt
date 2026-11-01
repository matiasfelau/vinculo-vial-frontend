package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.presentation.evidence.GalleryEvidenceSource
import ar.felaufernandezbottaro.vinculovial.presentation.evidence.NoOpGalleryEvidenceSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Variante `release`: sin galería. Ver el módulo homónimo de `src/debug`. */
@Module
@InstallIn(SingletonComponent::class)
object GalleryEvidenceModule {

    @Provides
    @Singleton
    fun provideGalleryEvidenceSource(): GalleryEvidenceSource = NoOpGalleryEvidenceSource
}
