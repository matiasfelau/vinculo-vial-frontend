package com.vinculovial.app.di

import com.vinculovial.app.data.edge.EdgeIdentityVerificationService
import com.vinculovial.app.data.edge.SelfieCompressor
import com.vinculovial.app.data.edge.document.DniBarcodeReader
import com.vinculovial.app.data.edge.document.DocumentFaceDetector
import com.vinculovial.app.data.edge.document.MlKitDniBarcodeReader
import com.vinculovial.app.data.edge.document.MlKitDocumentFaceDetector
import com.vinculovial.app.data.edge.document.MlKitDocumentPresenceSession
import com.vinculovial.app.data.edge.liveness.MlKitLivenessSession
import com.vinculovial.app.domain.signup.DocumentPresenceSession
import com.vinculovial.app.domain.signup.IdentityVerificationService
import com.vinculovial.app.domain.signup.LivenessSession
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
