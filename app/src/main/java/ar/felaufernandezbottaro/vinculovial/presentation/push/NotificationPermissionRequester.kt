package ar.felaufernandezbottaro.vinculovial.presentation.push

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Pide `POST_NOTIFICATIONS` justo después del login, nunca en el primer arranque en frío
 * -- ver CLAUDE.md. No bloquea nada: tanto si el usuario acepta como si rechaza, `onFinished`
 * se llama igual y la pantalla de abajo (Settings) queda utilizable de cualquier forma. La app
 * no promete notificaciones que no van a aparecer, pero tampoco impide seguir usándola sin
 * ellas.
 */
@Composable
fun NotificationPermissionRequester(onFinished: () -> Unit) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> onFinished() }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        when (decideNotificationPermissionStep(Build.VERSION.SDK_INT, alreadyGranted)) {
            NotificationPermissionStep.NOT_NEEDED -> onFinished()
            NotificationPermissionStep.SHOW_RATIONALE -> showRationale = true
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                onFinished()
            },
            title = { Text("Activá las notificaciones") },
            text = {
                Text(
                    "Te avisamos apenas alguien reporte un incidente sobre uno de tus " +
                        "vehículos vinculados. Podés cambiarlo cuando quieras desde los " +
                        "ajustes del sistema.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                ) { Text("Activar") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        onFinished()
                    },
                ) { Text("Ahora no") }
            },
        )
    }
}
