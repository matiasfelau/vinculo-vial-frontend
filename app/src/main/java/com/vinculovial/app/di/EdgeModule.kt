package com.vinculovial.app.di

import com.vinculovial.app.data.edge.EdgeIdentityVerificationService
import com.vinculovial.app.data.edge.LivenessDetector
import com.vinculovial.app.data.edge.OcrEngine
import com.vinculovial.app.data.edge.SimulatedLivenessDetector
import com.vinculovial.app.data.edge.SimulatedOcrEngine
import com.vinculovial.app.domain.signup.IdentityVerificationService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EdgeModule {

    /** Reemplazar por el motor real (ML Kit on-device) sin tocar el resto de la app. */
    @Binds
    abstract fun bindOcrEngine(impl: SimulatedOcrEngine): OcrEngine

    @Binds
    abstract fun bindLivenessDetector(impl: SimulatedLivenessDetector): LivenessDetector

    @Binds
    @Singleton
    abstract fun bindIdentityVerificationService(
        impl: EdgeIdentityVerificationService,
    ): IdentityVerificationService
}
