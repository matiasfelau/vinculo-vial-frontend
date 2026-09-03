package ar.felaufernandezbottaro.vinculovial.data.push

import android.content.SharedPreferences
import ar.felaufernandezbottaro.vinculovial.di.PushPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token en tránsito hacia el backend, en `SharedPreferences` sin cifrar: a diferencia de los
 * tokens de sesión, un fcm_token filtrado no compromete la cuenta -- el peor caso es que un
 * tercero pueda mandarle push a ese dispositivo, no una toma de sesión.
 */
@Singleton
class PendingFcmTokenStore @Inject constructor(
    @PushPreferences private val prefs: SharedPreferences,
) {

    fun savePendingRegistration(token: String) {
        prefs.edit().putString(KEY_PENDING_REGISTRATION, token).apply()
    }

    fun readPendingRegistration(): String? = prefs.getString(KEY_PENDING_REGISTRATION, null)

    fun clearPendingRegistration() {
        prefs.edit().remove(KEY_PENDING_REGISTRATION).apply()
    }

    /** Ver `LogoutUseCase`: el token cuya baja falló durante un logout, para reintentar. */
    fun savePendingDeletion(token: String) {
        prefs.edit().putString(KEY_PENDING_DELETION, token).apply()
    }

    fun readPendingDeletion(): String? = prefs.getString(KEY_PENDING_DELETION, null)

    fun clearPendingDeletion() {
        prefs.edit().remove(KEY_PENDING_DELETION).apply()
    }

    private companion object {
        const val KEY_PENDING_REGISTRATION = "pending_registration_token"
        const val KEY_PENDING_DELETION = "pending_deletion_token"
    }
}
