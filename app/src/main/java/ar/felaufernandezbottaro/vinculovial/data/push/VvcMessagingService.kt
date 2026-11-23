package ar.felaufernandezbottaro.vinculovial.data.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ar.felaufernandezbottaro.vinculovial.ExcludeFromCoverage
import ar.felaufernandezbottaro.vinculovial.MainActivity
import ar.felaufernandezbottaro.vinculovial.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * La push es sólo un disparador de atención: el contenido real de la alerta se consulta al
 * backend recién cuando el usuario toca la notificación y entra al buzón, así que nunca viaja
 * PII en el payload (ver CLAUDE.md).
 *
 * Hay tres clases de aviso. El de alerta nueva a los dueños es `data` puro (con `alert_type` y,
 * si lo trae, `alert_id`), así que el sistema nunca lo muestra solo: este servicio arma y publica
 * la notificación siempre él mismo. El de novedades al emisor (`data.type = SENT_UPDATED`) trae
 * además un bloque `notification`: con la app en segundo plano o cerrada lo muestra el sistema, y
 * con la app abierta llega acá y lo publica este servicio con el mismo título y cuerpo. El de
 * alertas sin leer entregadas al dueño (`data.type = INBOX_UPDATED`, sin `alert_id`) se trata
 * igual que el del emisor, y abre Recibidos.
 *
 * FCM puede rotar el token por su cuenta (reinstalación, restauración de backup, limpieza de
 * datos): sin capturar `onNewToken`, el dispositivo deja de recibir notificaciones en
 * silencio, sin que el usuario tenga forma de notarlo.
 */
@ExcludeFromCoverage(reason = "FirebaseMessagingService: lo instancia el sistema con FCM; la decisión de qué notificar vive en PushNotificationPlanner")
@AndroidEntryPoint
class VvcMessagingService : FirebaseMessagingService() {

    @Inject lateinit var tokenSyncManager: TokenSyncManager

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        tokenSyncManager.onTokenRotated(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val plan = PushNotificationPlanner.plan(
            data = message.data,
            notificationTitle = message.notification?.title,
            notificationBody = message.notification?.body,
        ) ?: return

        val contentIntent = PendingIntent.getActivity(
            this,
            plan.notificationId,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                plan.pushTypeExtra?.let { putExtra(EXTRA_PUSH_TYPE, it) }
                plan.alertType?.let { putExtra(EXTRA_ALERT_TYPE, it) }
                plan.alertId?.let { putExtra(EXTRA_ALERT_ID, it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(plan.title)
            .setContentText(plan.body)
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
            NotificationManagerCompat.from(this).notify(plan.notificationId, notification)
        }
    }
}
