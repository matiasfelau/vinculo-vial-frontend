package ar.felaufernandezbottaro.vinculovial.presentation.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/**
 * Pide el permiso de cámara justo antes de usarlo y sólo entonces muestra [content].
 *
 * Contempla los tres estados que importan: concedido, rechazado una vez (se puede volver
 * a pedir) y rechazado de forma permanente (sólo se arregla desde Ajustes del sistema).
 *
 * El motivo del permiso ([deniedRationale] / [permanentlyDeniedRationale]) es lo único que
 * cambia entre flujos (DNI, selfie, cédula del vehículo): la lógica de pedir, reintentar y
 * mandar a Ajustes es la misma para cualquiera de ellos, así que vive acá una sola vez.
 */
@Composable
fun CameraPermissionGate(
    onDenied: () -> Unit,
    deniedRationale: String = "Sin cámara no podemos verificar que el DNI sea tuyo. Las fotos se " +
        "analizan en tu dispositivo y se eliminan al instante.",
    permanentlyDeniedRationale: String = "Activá el permiso de cámara desde los ajustes del sistema " +
        "para validar tu identidad. Las fotos se analizan en tu dispositivo y se eliminan al instante.",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var askedOnce by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
        askedOnce = true
        // Si tras rechazar ya no corresponde mostrar explicación, el usuario marcó
        // "no volver a preguntar" y hay que mandarlo a Ajustes.
        permanentlyDenied = !isGranted && context.findActivity()?.shouldExplainCameraPermission() == false
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    // Al volver de Ajustes el permiso pudo cambiar sin que la app se entere.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !granted) {
                granted = context.hasCameraPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        granted -> content()
        askedOnce -> CameraPermissionDenied(
            permanentlyDenied = permanentlyDenied,
            rationale = if (permanentlyDenied) permanentlyDeniedRationale else deniedRationale,
            onRetry = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { context.openAppSettings() },
            onCancel = onDenied,
        )
        // Mientras el diálogo del sistema está arriba no se dibuja nada propio.
        else -> Unit
    }
}

@Composable
private fun CameraPermissionDenied(
    permanentlyDenied: Boolean,
    rationale: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = null,
            tint = VvNavy,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "Necesitamos la cámara",
            style = MaterialTheme.typography.headlineMedium,
            color = VvNavy,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        )
        Text(
            text = rationale,
            style = MaterialTheme.typography.bodyLarge,
            color = VvMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 28.dp),
        )

        if (permanentlyDenied) {
            VvPrimaryButton(text = "Abrir ajustes", onClick = onOpenSettings)
        } else {
            VvPrimaryButton(text = "Permitir cámara", onClick = onRetry)
        }

        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
            Text("Volver", color = VvNavy)
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Activity.shouldExplainCameraPermission(): Boolean =
    shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
