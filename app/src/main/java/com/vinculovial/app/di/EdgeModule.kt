package com.vinculovial.app.di

import android.content.Context
import com.vinculovial.app.BuildConfig
import com.vinculovial.app.data.edge.EdgeIdentityVerificationProvider
import com.vinculovial.app.data.edge.EdgeIdentityVerificationService
import com.vinculovial.app.data.edge.document.DniBarcodeReader
import com.vinculovial.app.data.edge.document.MlKitDniBarcodeReader
import com.vinculovial.app.data.edge.document.MlKitDocumentPresenceSession
import com.vinculovial.app.data.edge.face.FaceEmbedder
import com.vinculovial.app.data.edge.face.FaceLocator
import com.vinculovial.app.data.edge.face.MlKitFaceLocator
import com.vinculovial.app.data.edge.face.TfLiteFaceEmbedder
import com.vinculovial.app.data.edge.liveness.MlKitLivenessSession
import com.vinculovial.app.domain.signup.DocumentPresenceSession
import com.vinculovial.app.domain.signup.IdentityVerificationProvider
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
    abstract fun bindDniBarcodeReader(impl: MlKitDniBarcodeReader): DniBarcodeReader

    @Binds
    abstract fun bindFaceLocator(impl: MlKitFaceLocator): FaceLocator

    @Binds
    @Singleton
    abstract fun bindLivenessSession(impl: MlKitLivenessSession): LivenessSession

    @Binds
    @Singleton
    abstract fun bindDocumentPresenceSession(
        impl: MlKitDocumentPresenceSession,
    ): DocumentPresenceSession

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

        /**
         * Estrategia de verificación activa.
         *
         * Hoy es la local: todo el reconocimiento facial corre en el dispositivo y ninguna
         * imagen sale de él. La alternativa declarada es `RenaperIdentityVerificationProvider`
         * (stub), y cambiar a ella es cambiar esta única línea — pero **no es una decisión
         * puramente técnica**: implicaría que la selfie viaje a un servicio externo, lo que
         * contradice el Zero Retention que hoy sostiene todo el diseño. Ver el KDoc de esa
         * clase antes de tocar acá.
         */
        @Provides
        @Singleton
        fun provideIdentityVerificationProvider(
            faceLocator: FaceLocator,
            faceEmbedder: FaceEmbedder,
        ): IdentityVerificationProvider = EdgeIdentityVerificationProvider(
            faceLocator = faceLocator,
            faceEmbedder = faceEmbedder,
            faceMatchThreshold = BuildConfig.FACE_MATCH_THRESHOLD,
        )

        @Provides
        @Singleton
        fun provideIdentityVerificationService(
            barcodeReader: DniBarcodeReader,
            faceLocator: FaceLocator,
            verificationProvider: IdentityVerificationProvider,
            @DefaultDispatcher dispatcher: CoroutineDispatcher,
        ): IdentityVerificationService = EdgeIdentityVerificationService(
            barcodeReader = barcodeReader,
            faceLocator = faceLocator,
            verificationProvider = verificationProvider,
            dispatcher = dispatcher,
        )
    }
}
