package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.presentation.evidence.DebugGalleryEvidenceSource
import ar.felaufernandezbottaro.vinculovial.presentation.evidence.GalleryEvidenceSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/** Variante `debug`: la galería (Photo Picker) está disponible. Ver el módulo homónimo de `src/release`. */
@Module
@InstallIn(SingletonComponent::class)
object GalleryEvidenceModule {

    @Provides
    @Singleton
    fun provideGalleryEvidenceSource(@IoDispatcher ioDispatcher: CoroutineDispatcher): GalleryEvidenceSource =
        DebugGalleryEvidenceSource(ioDispatcher)
}
