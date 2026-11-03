package ar.felaufernandezbottaro.vinculovial.di

import android.content.Context
import android.content.SharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.AndroidSessionKeyProvisioner
import ar.felaufernandezbottaro.vinculovial.data.session.SessionAead
import ar.felaufernandezbottaro.vinculovial.data.session.SessionKeyProvisioner
import com.google.crypto.tink.Aead
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    @Provides
    @Singleton
    fun provideSessionPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(TOKENS_PREFS_FILE_NAME, Context.MODE_PRIVATE)

    /** Detrás de [SessionKeyProvisioner] para poder probar `SessionAead` sin un Keystore real. */
    @Provides
    @Singleton
    fun provideSessionKeyProvisioner(@ApplicationContext context: Context): SessionKeyProvisioner =
        AndroidSessionKeyProvisioner(context)

    /**
     * El `Aead` con el que `SecureTokenStore` cifra los tokens. Es [SessionAead] -- y no el
     * primitivo de Tink suelto -- porque la baja de cuenta tiene que poder destruir su clave y
     * regenerarla: ver esa clase.
     */
    @Provides
    @Singleton
    fun provideSessionAead(sessionAead: SessionAead): Aead = sessionAead

    private const val TOKENS_PREFS_FILE_NAME = "vinculo_vial_session_tokens"
}
