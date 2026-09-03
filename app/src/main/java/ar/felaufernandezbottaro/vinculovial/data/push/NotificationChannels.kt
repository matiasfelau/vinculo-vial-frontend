package ar.felaufernandezbottaro.vinculovial.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat

const val ALERTS_CHANNEL_ID = "alerts"

/**
 * Crear el canal es idempotente (el sistema ignora una segunda creación con el mismo id), así
 * que se puede invocar en cada arranque de la app sin guardas propias. Sólo existe desde
 * Android 8 (API 26) -- versión mínima soportada por este proyecto, pero el check se deja
 * explícito porque `NotificationChannel` no existe en versiones anteriores.
 */
fun ensureAlertsChannelCreated(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val channel = NotificationChannel(
        ALERTS_CHANNEL_ID,
        "Alertas de tus vehículos",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "Avisos cuando alguien reporta un incidente sobre un vehículo vinculado a tu cuenta."
    }

    ContextCompat.getSystemService(context, NotificationManager::class.java)
        ?.createNotificationChannel(channel)
}
