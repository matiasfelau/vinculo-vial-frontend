package com.vinculovial.app.di

import android.content.Context
import com.vinculovial.app.BuildConfig
import com.vinculovial.app.data.edge.EdgeIdentityVerificationService
import com.vinculovial.app.data.edge.document.DniBarcodeReader
import com.vinculovial.app.data.edge.document.MlKitPdf417DniReader
import com.vinculovial.app.data.edge.face.FaceEmbedder
import com.vinculovial.app.data.edge.face.FaceLocator
import com.vinculovial.app.data.edge.face.MlKitFaceLocator
import com.vinculovial.app.data.edge.face.TfLiteFaceEmbedder
import com.vinculovial.app.data.edge.liveness.MlKitLivenessSession
import com.vinculovial.app.domain.signup.IdentityVerificationService
import com.vinculovial.app.domain.signup.LivenessSession
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EdgeModule {

    @Binds
    abstract fun bindDniBarcodeReader(impl: MlKitPdf417DniReader): DniBarcodeReader

    @Binds
    abstract fun bindFaceLocator(impl: MlKitFaceLocator): FaceLocator

    @Binds
    @Singleton
    abstract fun bindLivenessSession(impl: MlKitLivenessSession): LivenessSession

    companion object {

        /**
         * Normalización estándar de MobileFaceNet: `(pixel - 127.5) / 128`. Si se cambia
         * el modelo por uno entrenado con otra convención, hay que ajustar estos valores.
         */
        private const val INPUT_MEAN = 127.5f
        private const val INPUT_SCALE = 128f

        @Provides
        @Singleton
        fun provideFaceEmbedder(@ApplicationContext context: Context): FaceEmbedder =
            TfLiteFaceEmbedder(
                context = context,
                modelAssetName = BuildConfig.FACE_EMBEDDER_MODEL_ASSET,
                inputScale = INPUT_SCALE,
                inputMean = INPUT_MEAN,
            )

        @Provides
        @Singleton
        fun provideIdentityVerificationService(
            barcodeReader: DniBarcodeReader,
            faceLocator: FaceLocator,
            faceEmbedder: FaceEmbedder,
            @DefaultDispatcher dispatcher: CoroutineDispatcher,
        ): IdentityVerificationService = EdgeIdentityVerificationService(
            barcodeReader = barcodeReader,
            faceLocator = faceLocator,
            faceEmbedder = faceEmbedder,
            faceMatchThreshold = BuildConfig.FACE_MATCH_THRESHOLD,
            dispatcher = dispatcher,
        )
    }
}
