package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.BuildConfig
import ar.felaufernandezbottaro.vinculovial.data.emailverification.EmailVerificationRepositoryImpl
import ar.felaufernandezbottaro.vinculovial.data.recovery.PasswordResetRepositoryImpl
import ar.felaufernandezbottaro.vinculovial.data.recovery.RecoverySessionCleanerImpl
import ar.felaufernandezbottaro.vinculovial.domain.emailverification.EmailVerificationRepository
import ar.felaufernandezbottaro.vinculovial.domain.recovery.PasswordResetRepository
import ar.felaufernandezbottaro.vinculovial.domain.recovery.RecoverySessionCleaner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Segundos de la cuenta regresiva del botón "Reenviar" del código por email. Es el mismo
 * `EMAIL_CODE_RESEND_SECONDS` que usa Identity para su throttle: tienen que coincidir.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EmailCodeResendSeconds

/** Verificación del email en el alta y recuperación de contraseña (US-13): cableado. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthFlowModule {

    @Binds
    @Singleton
    abstract fun bindEmailVerificationRepository(impl: EmailVerificationRepositoryImpl): EmailVerificationRepository

    @Binds
    @Singleton
    abstract fun bindPasswordResetRepository(impl: PasswordResetRepositoryImpl): PasswordResetRepository

    @Binds
    @Singleton
    abstract fun bindRecoverySessionCleaner(impl: RecoverySessionCleanerImpl): RecoverySessionCleaner

    companion object {

        @Provides
        @EmailCodeResendSeconds
        fun provideEmailCodeResendSeconds(): Int = BuildConfig.EMAIL_CODE_RESEND_SECONDS
    }
}
