package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.data.edge.EdgeIdentityVerificationService
import ar.felaufernandezbottaro.vinculovial.data.edge.SelfieCompressor
import ar.felaufernandezbottaro.vinculovial.data.edge.document.MlKitDocumentScanSession
import ar.felaufernandezbottaro.vinculovial.data.edge.liveness.MlKitLivenessSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.DocumentScanSession
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
    @Singleton
    abstract fun bindLivenessSession(impl: MlKitLivenessSession): LivenessSession

    @Binds
    @Singleton
    abstract fun bindDocumentScanSession(impl: MlKitDocumentScanSession): DocumentScanSession

    companion object {

        /**
         * El servicio quedó reducido a sostener las capturas y comprimir la selfie: la
         * lectura del documento se resuelve entera en `DocumentScanSession` (escaneo en
         * vivo) y la verificación de identidad la hace el backend contra RENAPER.
         *
         * [SelfieCompressor] no necesita `@Provides` — tiene `@Inject constructor()` propio.
         */
        @Provides
        @Singleton
        fun provideIdentityVerificationService(
            selfieCompressor: SelfieCompressor,
            @DefaultDispatcher dispatcher: CoroutineDispatcher,
        ): IdentityVerificationService = EdgeIdentityVerificationService(
            selfieCompressor = selfieCompressor,
            dispatcher = dispatcher,
        )
    }
}
