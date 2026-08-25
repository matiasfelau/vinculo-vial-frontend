package ar.felaufernandezbottaro.vinculovial.presentation.scan

import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.felaufernandezbottaro.vinculovial.domain.scan.model.OverlayRect
import ar.felaufernandezbottaro.vinculovial.presentation.camera.awaitCameraProviderInternal
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvCircleIconButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/**
 * Escaneo en vivo de la patente: cámara + overlay rectangular, sin obturador -- mismo enfoque
 * que `ScanCedulaScreen` (US-04). Este primer paso sólo abre la cámara y dibuja el recuadro;
 * el análisis de frames (recorte + OCR + filtro de formato) se integra en branches
 * posteriores, sobre la misma pantalla.
 */
@Composable
fun ScanPlateScreen(onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var failure by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    BackHandler { showCancelDialog = true }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(Unit) {
        failure = try {
            val provider = context.awaitCameraProviderInternal()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            cameraProvider = provider
            null
        } catch (e: Exception) {
            "No pudimos abrir la cámara. Cerrá otras apps que la estén usando e intentá de nuevo."
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraProvider?.unbindAll() }
    }

    LaunchedEffect(torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        PlateViewfinder()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            VvCircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cancelar",
                onClick = { showCancelDialog = true },
            )
            Text(
                text = failure ?: "Encuadrá la patente del vehículo",
                style = MaterialTheme.typography.labelLarge,
                color = if (failure != null) MaterialTheme.colorScheme.error else Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
            )
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                VvCircleIconButton(
                    icon = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = if (torchOn) "Apagar la luz" else "Encender la luz",
                    onClick = { torchOn = !torchOn },
                )
            }
        }

        if (showCancelDialog) {
            CancelScanDialog(
                onConfirm = {
                    showCancelDialog = false
                    onCancel()
                },
                onDismiss = { showCancelDialog = false },
            )
        }
    }
}

@Composable
private fun CancelScanDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Deseás cancelar el escaneo?") },
        text = { Text("Vas a volver sin reportar nada.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sí, cancelar", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Seguir", color = VvNavy) }
        },
    )
}

/**
 * Marco de escaneo rectangular (proporción de patente, no cuadrado como el de la cédula):
 * dibuja exactamente [OverlayRect.PLATE], la misma región que recorta `FrameCropper`.
 */
@Composable
private fun PlateViewfinder() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val overlay = OverlayRect.PLATE
        val left = size.width * overlay.leftFraction
        val top = size.height * overlay.topFraction
        val width = size.width * overlay.widthFraction
        val height = size.height * overlay.heightFraction
        val arm = minOf(width, height) * 0.22f
        val strokeWidth = 5f
        val color = Color.White

        listOf(
            Offset(left, top) to Pair(arm, arm),
            Offset(left + width, top) to Pair(-arm, arm),
            Offset(left, top + height) to Pair(arm, -arm),
            Offset(left + width, top + height) to Pair(-arm, -arm),
        ).forEach { (corner, arms) ->
            val (horizontal, vertical) = arms
            drawLine(color, corner, corner.copy(x = corner.x + horizontal), strokeWidth, StrokeCap.Round)
            drawLine(color, corner, corner.copy(y = corner.y + vertical), strokeWidth, StrokeCap.Round)
        }
    }
}
