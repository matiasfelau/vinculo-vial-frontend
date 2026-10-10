package ar.felaufernandezbottaro.vinculovial.data.inbox

import android.content.SharedPreferences
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReadAlertsStore

/**
 * Guarda los ids de las alertas abiertas en las preferencias privadas de la app. Un `alert_id`
 * es un UUID sin relación con ningún dato personal, así que no hace falta cifrarlo; igual vive
 * fuera de los backups (`allowBackup=false`) y nunca sale del dispositivo.
 *
 * Los ids se guardan en un único string, uno por línea y del más viejo al más nuevo, y se
 * conservan a lo sumo [MAX_IDS]: al pasarse se descartan los más viejos, así el archivo no
 * crece sin límite.
 */
class SharedPreferencesReadAlertsStore(
    private val preferences: SharedPreferences,
) : ReadAlertsStore {

    override fun readIds(): Set<String> = read().toSet()

    @Synchronized
    override fun markRead(alertId: String) {
        if (alertId.isBlank() || SEPARATOR in alertId) return

        val ids = read().toMutableList()
        if (alertId in ids) return

        ids += alertId
        val kept = ids.takeLast(MAX_IDS)
        preferences.edit().putString(KEY, kept.joinToString(SEPARATOR)).apply()
    }

    private fun read(): List<String> =
        preferences.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    companion object {
        const val MAX_IDS = 500
        private const val KEY = "read_alert_ids"
        private const val SEPARATOR = "\n"
    }
}
