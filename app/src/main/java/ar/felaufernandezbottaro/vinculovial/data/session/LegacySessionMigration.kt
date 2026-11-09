package ar.felaufernandezbottaro.vinculovial.data.session

import android.content.Context
import android.util.Log
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lo que dejó el almacenamiento de sesión anterior a US-12: los dos tokens cifrados con Tink
 * (AES-256-GCM), el keyset que los cifraba y la clave maestra del Keystore que envolvía ese
 * keyset. Detrás de una interfaz para probar la migración sin un dispositivo.
 */
interface LegacySessionStorage {

    /** Queda algo del esquema anterior. */
    fun hasLegacyData(): Boolean

    /** Borra todo lo del esquema anterior. */
    fun purge()
}

/**
 * Al actualizar, los tokens viejos se borran y el usuario entra una vez con la contraseña: el
 * modelo nuevo no los lee ni los migra. Es idempotente: sin restos del esquema anterior no hace
 * nada, así que puede correr en cada arranque.
 */
@Singleton
class LegacySessionMigration @Inject constructor(
    private val storage: LegacySessionStorage,
) {

    fun run() {
        if (storage.hasLegacyData()) storage.purge()
    }
}

class AndroidLegacySessionStorage(private val context: Context) : LegacySessionStorage {

    override fun hasLegacyData(): Boolean =
        context.getSharedPreferences(TOKENS_PREFS_FILE_NAME, Context.MODE_PRIVATE).all.isNotEmpty() ||
            context.getSharedPreferences(KEYSET_PREFS_FILE_NAME, Context.MODE_PRIVATE).all.isNotEmpty()

    override fun purge() {
        // Primero la clave maestra y después los archivos: si el proceso muere a la mitad, los
        // archivos que sobran siguen marcando que falta limpiar y el próximo arranque lo termina.
        deleteMasterKey()
        context.deleteSharedPreferences(TOKENS_PREFS_FILE_NAME)
        context.deleteSharedPreferences(KEYSET_PREFS_FILE_NAME)
    }

    private fun deleteMasterKey() {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
        } catch (e: GeneralSecurityException) {
            // Una clave huérfana en el Keystore es inofensiva: sin el keyset no abre nada.
            Log.w(TAG, "No se pudo borrar la clave maestra anterior: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "LegacySessionMigration"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TOKENS_PREFS_FILE_NAME = "vinculo_vial_session_tokens"
        const val KEYSET_PREFS_FILE_NAME = "vinculo_vial_session_keyset_prefs"
        const val MASTER_KEY_ALIAS = "vinculo_vial_session_master_key"
    }
}
