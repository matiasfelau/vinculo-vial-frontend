package ar.felaufernandezbottaro.vinculovial.data.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ar.felaufernandezbottaro.vinculovial.MainActivity
import ar.felaufernandezbottaro.vinculovial.R
import ar.felaufernandezbottaro.vinculovial.data.inbox.mapAlertTypeToDisplay
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * La push es sólo un disparador de atención: el payload es `data`, no `notification`, así que
 * el sistema nunca la muestra solo -- este servicio arma y publica la notificación siempre él
 * mismo, en cualquier estado de la app (foreground, background o cerrada). Eso es a propósito:
 * es la única forma de garantizar que nunca viaje PII en el payload (ver CLAUDE.md), porque el
 * contenido real de la alerta se consulta al backend recién cuando el usuario toca la
 * notificación y entra a la Bandeja de Entrada.
 *
 * FCM puede rotar el token por su cuenta (reinstalación, restauración de backup, limpieza de
 * datos): sin capturar `onNewToken`, el dispositivo deja de recibir notificaciones en
 * silencio, sin que el usuario tenga forma de notarlo.
 */
@AndroidEntryPoint
class VvcMessagingService : FirebaseMessagingService() {

    @Inject lateinit var tokenSyncManager: TokenSyncManager

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        tokenSyncManager.onTokenRotated(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val alertType = message.data[EXTRA_ALERT_TYPE] ?: return
        val display = mapAlertTypeToDisplay(alertType)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ALERT_TYPE, alertType)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(display.label)
            .setContentText(GENERIC_BODY)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // El permiso es opcional (Android 13+, ver NotificationPermissionRequester): si el
        // usuario lo negó, la app nunca promete notificaciones que no van a aparecer, así que
        // simplemente no se publica en vez de arriesgar una SecurityException.
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 1
        const val GENERIC_BODY = "Tenés una alerta nueva sobre uno de tus vehículos vinculados."
    }
}
