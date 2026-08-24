package ar.felaufernandezbottaro.vinculovial.presentation.vehicle

import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.QrCedulaScanner
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaData
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaScanStage
import ar.felaufernandezbottaro.vinculovial.presentation.camera.awaitCameraProviderInternal
import ar.felaufernandezbottaro.vinculovial.presentation.camera.toCameraFrame
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvCircleIconButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvGreen
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

/**
 * Escaneo del QR de la cédula del vehículo: en vivo, sin foto ni obturador -- mismo enfoque
 * que `DocumentScanScreen` (US-01). A diferencia de esa pantalla, acá el resultado del
 * escaneo no navega a otro lado: la publicación del vínculo (cifrado + POST) ocurre en esta
 * misma pantalla, así que también refleja [uiState] (vinculando / vinculado / error).
 */
@Composable
fun ScanCedulaScreen(
    scanSession: QrCedulaScanner,
    uiState: VehicleUiState,
    onCedulaScanned: (CedulaData) -> Unit,
    onErrorDismissed: () -> Unit,
    onLinked: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scanState by scanSession.state.collectAsStateWithLifecycle()

    var failure by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    // Mientras se está publicando el vínculo no tiene sentido dejar que "Atrás" corte la
    // request a mitad de camino.
    BackHandler(enabled = !uiState.isLinking) { showCancelDialog = true }

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
        scanSession.start()

        failure = try {
            val provider = context.awaitCameraProviderInternal()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                val frame = try {
                    imageProxy.toCameraFrame()
                } finally {
                    imageProxy.close()
                }
                if (frame != null) {
                    runBlocking { scanSession.submitFrame(frame) }
                }
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
            scanSession.close()
        }
    }

    LaunchedEffect(torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    // Confirmación háptica en el instante de la lectura, y una pausa corta antes de arrancar
    // el cifrado + POST: sin ella el cambio de estado se siente como un salto sin causa.
    LaunchedEffect(scanState.isDone) {
        val cedula = scanState.cedula
        if (scanState.isDone && cedula != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(SUCCESS_HOLD_MS)
            onCedulaScanned(cedula)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        ScannerViewfinder(stage = scanState.stage, status = uiState.status)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            VvCircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cancelar",
                enabled = !uiState.isLinking,
                onClick = { showCancelDialog = true },
            )
            Text(
                text = "Escaneá el QR de la cédula del vehículo",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
            )
            // El plástico de la cédula refleja, y con poca luz el código se pierde -- mismo
            // motivo que en DocumentScanScreen.
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = failure ?: statusHeadline(uiState.status, scanState.stage),
                style = MaterialTheme.typography.headlineSmall,
                color = when {
                    failure != null -> VvOrange
                    uiState.status is VehicleStatus.Linked -> VvGreen
                    uiState.status is VehicleStatus.Failed -> VvOrange
                    scanState.stage == CedulaScanStage.NOT_A_CEDULA -> VvOrange
                    else -> Color.White
                },
                textAlign = TextAlign.Center,
            )

            if (uiState.isLinking) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(top = 4.dp))
            }
        }

        if (uiState.status is VehicleStatus.Linked) {
            LinkedOverlay(onFinish = onLinked)
        }

        val failedStatus = uiState.status as? VehicleStatus.Failed
        if (failedStatus != null) {
            VehicleErrorDialog(
                error = failedStatus.error,
                onDismiss = {
                    onErrorDismissed()
                    scanSession.start()
                },
            )
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

private fun statusHeadline(status: VehicleStatus, stage: CedulaScanStage): String = when (status) {
    VehicleStatus.Linking -> "Vinculando el vehículo…"
    VehicleStatus.Linked -> "¡Listo!"
    is VehicleStatus.Failed -> "No pudimos vincular el vehículo"
    VehicleStatus.Idle -> when (stage) {
        CedulaScanStage.SEARCHING -> "Apuntá al QR de la cédula"
        CedulaScanStage.NOT_A_CEDULA -> "Ese código no es de una cédula"
        CedulaScanStage.DECODED -> "¡Listo!"
    }
}

@Composable
private fun LinkedOverlay(onFinish: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = VvGreen,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = "Vehículo vinculado a tu cuenta",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            VvPrimaryButton(text = "Continuar", onClick = onFinish)
        }
    }
}

@Composable
private fun VehicleErrorDialog(error: LinkVehicleError, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No pudimos vincular el vehículo") },
        text = { Text(error.message()) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendido", color = VvNavy) }
        },
    )
}

@Composable
private fun CancelScanDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Deseás cancelar la vinculación?") },
        text = { Text("Vas a perder el progreso de este escaneo.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sí, cancelar", color = VvOrange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Seguir", color = VvNavy) }
        },
    )
}

/**
 * Marco de escaneo: recuadro cuadrado (a diferencia del ID-1 de `DocumentScanScreen`) ya
 * que un QR, a diferencia de un PDF417 sobre una tarjeta, no tiene una proporción rectangular
 * que orientar.
 */
@Composable
private fun ScannerViewfinder(stage: CedulaScanStage, status: VehicleStatus) {
    val target = when {
        status != VehicleStatus.Idle -> VvGreen
        stage == CedulaScanStage.NOT_A_CEDULA -> VvOrange
        else -> Color.White
    }
    val cornerColor by androidx.compose.animation.animateColorAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "corners",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = size.width * 0.72f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val arm = side * 0.12f
        val strokeWidth = 5f

        listOf(
            Offset(left, top) to Pair(arm, arm),
            Offset(left + side, top) to Pair(-arm, arm),
            Offset(left, top + side) to Pair(arm, -arm),
            Offset(left + side, top + side) to Pair(-arm, -arm),
        ).forEach { (corner, arms) ->
            val (horizontal, vertical) = arms
            drawLine(cornerColor, corner, corner.copy(x = corner.x + horizontal), strokeWidth, StrokeCap.Round)
            drawLine(cornerColor, corner, corner.copy(y = corner.y + vertical), strokeWidth, StrokeCap.Round)
        }
    }
}

/** Cuánto se sostiene el estado de éxito del escaneo antes de arrancar el cifrado + POST. */
private const val SUCCESS_HOLD_MS = 450L
