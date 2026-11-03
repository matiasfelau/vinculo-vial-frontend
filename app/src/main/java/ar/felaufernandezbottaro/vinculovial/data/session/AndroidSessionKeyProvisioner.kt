package ar.felaufernandezbottaro.vinculovial.data.session

import android.content.Context
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoConfigurationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * AES-256-GCM cuya clave vive envuelta por el Android Keystore: el keyset en sí (el archivo de
 * `withSharedPref`) queda ilegible fuera de este dispositivo, y el Keystore nunca entrega la
 * clave en sí, sólo la usa para envolver/desenvolver.
 *
 * Reemplaza a `EncryptedSharedPreferences` (deprecada por Google en 2025 sin sucesora directa)
 * con el mismo Tink que ya trae el proyecto para HPKE.
 *
 * Son dos piezas, y [destroy] borra las dos: el keyset envuelto (en `SharedPreferences`) y la
 * clave maestra del Keystore que lo envuelve. Con sólo una de las dos borrada, la otra quedaría
 * huérfana en el dispositivo.
 */
class AndroidSessionKeyProvisioner(private val context: Context) : SessionKeyProvisioner {

    override fun provision(): Aead {
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

    override fun destroy() {
        // Primero el keyset y después la clave maestra: si el proceso muere entre las dos, queda
        // una clave maestra sin keyset (inofensiva, el próximo arranque la reutiliza) y no un
        // keyset que ya no se puede desenvolver.
        val keysetRemoved = context
            .getSharedPreferences(KEYSET_PREFS_FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEYSET_NAME)
            .commit()
        if (!keysetRemoved) throw CryptoOperationException("No se pudo borrar el keyset de sesión")

        try {
            AndroidKeystoreKmsClient().deleteKey(MASTER_KEY_URI)
        } catch (e: GeneralSecurityException) {
            throw CryptoOperationException("No se pudo borrar la clave maestra de sesión del Keystore", e)
        }
    }

    private companion object {
        const val KEYSET_NAME = "vinculo_vial_session_keyset"
        const val KEYSET_PREFS_FILE_NAME = "vinculo_vial_session_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://vinculo_vial_session_master_key"
    }
}
