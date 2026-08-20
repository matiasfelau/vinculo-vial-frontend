package ar.felaufernandezbottaro.vinculovial.di

import android.content.Context
import android.content.SharedPreferences
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoConfigurationException
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.GeneralSecurityException
import java.io.IOException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    @Provides
    @Singleton
    fun provideSessionPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(TOKENS_PREFS_FILE_NAME, Context.MODE_PRIVATE)

    /**
     * AES-256-GCM cuya clave vive envuelta por el Android Keystore: el keyset en sí (el
     * archivo de `withSharedPref`) queda ilegible fuera de este dispositivo, y el Keystore
     * nunca entrega la clave en sí, sólo la usa para envolver/desenvolver.
     *
     * Reemplaza a `EncryptedSharedPreferences` (deprecada por Google en 2025 sin sucesora
     * directa) con el mismo Tink que ya trae el proyecto para HPKE.
     */
    @Provides
    @Singleton
    fun provideSessionAead(@ApplicationContext context: Context): Aead {
        AeadConfig.register()

        val keysetHandle = try {
            AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_FILE_NAME)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
        } catch (e: GeneralSecurityException) {
            throw CryptoConfigurationException("No se pudo inicializar el Aead de sesión con Keystore", e)
        } catch (e: IOException) {
            throw CryptoConfigurationException("No se pudo leer/escribir el keyset de sesión", e)
        }

        return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private const val KEYSET_NAME = "vinculo_vial_session_keyset"
    private const val KEYSET_PREFS_FILE_NAME = "vinculo_vial_session_keyset_prefs"
    private const val MASTER_KEY_URI = "android-keystore://vinculo_vial_session_master_key"
    private const val TOKENS_PREFS_FILE_NAME = "vinculo_vial_session_tokens"
}
