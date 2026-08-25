package ar.felaufernandezbottaro.vinculovial.presentation.scan

import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.CheckVehicleError
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.domain.scan.model.OverlayRect
import ar.felaufernandezbottaro.vinculovial.presentation.alert.AlertTypeBottomSheet
import ar.felaufernandezbottaro.vinculovial.presentation.camera.awaitCameraProviderInternal
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvCircleIconButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvGreen
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

/**
 * Escaneo en vivo de la patente: cámara + overlay rectangular, sin obturador. El resultado no
 * navega a otro lado -- el flujo de dos pasos (check + alert) ocurre en esta misma pantalla,
 * así que también refleja [uiState] -- mismo enfoque que `ScanCedulaScreen` (US-04).
 */
@Composable
fun ScanPlateScreen(
    uiState: ScanUiState,
    onFrame: suspend (ImageProxy) -> Unit,
    onPlateDetected: (String) -> Unit,
    onAlertTypeSelected: (AlertType) -> Unit,
    onAlertTypeDismissed: () -> Unit,
    onNotInNetworkDismissed: () -> Unit,
    onErrorDismissed: () -> Unit,
    onAlertSentFinished: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    var failure by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = !uiState.isBusy) { showCancelDialog = true }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .build()
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(Unit) {
        failure = try {
            val provider = context.awaitCameraProviderInternal()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                runBlocking { onFrame(imageProxy) }
            }

            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
            )
            cameraProvider = provider
            null
        } catch (e: Exception) {
            "No pudimos abrir la cámara. Cerrá otras apps que la estén usando e intentá de nuevo."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    // Confirmación háptica en el instante de la lectura, y una pausa corta antes de arrancar
    // el check -- sin ella el cambio de estado se siente como un salto sin causa.
    LaunchedEffect(uiState.status) {
        val status = uiState.status
        if (status is ScanStatus.Detected) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(SUCCESS_HOLD_MS)
            onPlateDetected(status.patente)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        PlateViewfinder(status = uiState.status)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            VvCircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cancelar",
                enabled = !uiState.isBusy,
                onClick = { showCancelDialog = true },
            )
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                VvCircleIconButton(
                    icon = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = if (torchOn) "Apagar la luz" else "Encender la luz",
                    onClick = { torchOn = !torchOn },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = failure ?: statusHeadline(uiState.status),
                style = MaterialTheme.typography.headlineSmall,
                color = when {
                    failure != null -> VvOrange
                    uiState.status is ScanStatus.AlertSent -> VvGreen
                    uiState.status is ScanStatus.CheckFailed -> VvOrange
                    uiState.status is ScanStatus.AlertFailed -> VvOrange
                    uiState.status is ScanStatus.NotInNetwork -> VvOrange
                    else -> Color.White
                },
                textAlign = TextAlign.Center,
            )

            if (uiState.isBusy) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(top = 4.dp))
            }
        }

        if (uiState.status is ScanStatus.AwaitingAlertType) {
            AlertTypeBottomSheet(
                onAlertTypeSelected = onAlertTypeSelected,
                onDismiss = onAlertTypeDismissed,
            )
        }

        if (uiState.status is ScanStatus.AlertSent) {
            AlertSentOverlay(onFinish = onAlertSentFinished)
        }

        if (uiState.status is ScanStatus.NotInNetwork) {
            NotInNetworkDialog(onDismiss = onNotInNetworkDismissed)
        }

        val checkFailed = uiState.status as? ScanStatus.CheckFailed
        if (checkFailed != null) {
            ScanErrorDialog(message = checkFailed.error.message(), onDismiss = onErrorDismissed)
        }

        val alertFailed = uiState.status as? ScanStatus.AlertFailed
        if (alertFailed != null) {
            ScanErrorDialog(message = alertFailed.error.message(), onDismiss = onErrorDismissed)
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

private fun statusHeadline(status: ScanStatus): String = when (status) {
    is ScanStatus.Scanning -> "Encuadrá la patente del vehículo"
    is ScanStatus.Detected -> "¡Patente reconocida!"
    ScanStatus.Checking -> "Verificando la patente…"
    ScanStatus.NotInNetwork -> "El vehículo no está en la red"
    ScanStatus.AwaitingAlertType -> "Elegí qué querés reportar"
    ScanStatus.SendingAlert -> "Enviando el reporte…"
    ScanStatus.AlertSent -> "¡Listo!"
    is ScanStatus.CheckFailed -> "No pudimos verificar la patente"
    is ScanStatus.AlertFailed -> "No pudimos enviar el reporte"
}

@Composable
private fun AlertSentOverlay(onFinish: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = VvGreen,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            Text(
                text = "Reporte enviado de forma anónima",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            VvPrimaryButton(text = "Continuar", onClick = onFinish)
        }
    }
}

@Composable
private fun NotInNetworkDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("El vehículo no está en la red") },
        text = { Text("Esta patente no corresponde a ningún vehículo vinculado en Vínculo Vial.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendido", color = VvNavy) }
        },
    )
}

@Composable
private fun ScanErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ocurrió un problema") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendido", color = VvNavy) }
        },
    )
}

@Composable
private fun CancelScanDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Deseás cancelar el escaneo?") },
        text = { Text("Vas a volver sin reportar nada.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sí, cancelar", color = VvOrange) }
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
private fun PlateViewfinder(status: ScanStatus) {
    val target = if (status is ScanStatus.Scanning) Color.White else VvGreen
    val cornerColor by androidx.compose.animation.animateColorAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "corners",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val overlay = OverlayRect.PLATE
        val left = size.width * overlay.leftFraction
        val top = size.height * overlay.topFraction
        val width = size.width * overlay.widthFraction
        val height = size.height * overlay.heightFraction
        val arm = minOf(width, height) * 0.22f
        val strokeWidth = 5f

        listOf(
            Offset(left, top) to Pair(arm, arm),
            Offset(left + width, top) to Pair(-arm, arm),
            Offset(left, top + height) to Pair(arm, -arm),
            Offset(left + width, top + height) to Pair(-arm, -arm),
        ).forEach { (corner, arms) ->
            val (horizontal, vertical) = arms
            drawLine(cornerColor, corner, corner.copy(x = corner.x + horizontal), strokeWidth, StrokeCap.Round)
            drawLine(cornerColor, corner, corner.copy(y = corner.y + vertical), strokeWidth, StrokeCap.Round)
        }
    }
}

/** Cuánto se sostiene el estado de éxito del escaneo antes de arrancar el check. */
private const val SUCCESS_HOLD_MS = 450L
