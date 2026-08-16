package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.data.edge.EdgeIdentityVerificationService
import ar.felaufernandezbottaro.vinculovial.data.edge.SelfieCompressor
import ar.felaufernandezbottaro.vinculovial.data.edge.document.DniBarcodeReader
import ar.felaufernandezbottaro.vinculovial.data.edge.document.DocumentFaceDetector
import ar.felaufernandezbottaro.vinculovial.data.edge.document.MlKitDniBarcodeReader
import ar.felaufernandezbottaro.vinculovial.data.edge.document.MlKitDocumentFaceDetector
import ar.felaufernandezbottaro.vinculovial.data.edge.document.MlKitDocumentPresenceSession
import ar.felaufernandezbottaro.vinculovial.data.edge.liveness.MlKitLivenessSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.DocumentPresenceSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.IdentityVerificationService
import ar.felaufernandezbottaro.vinculovial.domain.signup.LivenessSession
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EdgeModule {

    @Binds
    abstract fun bindDniBarcodeReader(impl: MlKitDniBarcodeReader): DniBarcodeReader

    @Binds
    abstract fun bindDocumentFaceDetector(impl: MlKitDocumentFaceDetector): DocumentFaceDetector

    @Binds
    @Singleton
    abstract fun bindLivenessSession(impl: MlKitLivenessSession): LivenessSession

    @Binds
    @Singleton
    abstract fun bindDocumentPresenceSession(impl: MlKitDocumentPresenceSession): DocumentPresenceSession

    companion object {

        /**
         * Ya no hay motor de reconocimiento facial que cablear acá: la verificación de
         * identidad la hace el backend contra RENAPER. [SelfieCompressor] no necesita
         * `@Provides` — tiene `@Inject constructor()` propio, Hilt lo resuelve solo.
         */
        @Provides
        @Singleton
        fun provideIdentityVerificationService(
            barcodeReader: DniBarcodeReader,
            faceDetector: DocumentFaceDetector,
            selfieCompressor: SelfieCompressor,
            @DefaultDispatcher dispatcher: CoroutineDispatcher,
        ): IdentityVerificationService = EdgeIdentityVerificationService(
            barcodeReader = barcodeReader,
            faceDetector = faceDetector,
            selfieCompressor = selfieCompressor,
            dispatcher = dispatcher,
        )
    }
}
