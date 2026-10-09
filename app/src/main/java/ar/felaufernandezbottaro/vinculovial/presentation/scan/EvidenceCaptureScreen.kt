package ar.felaufernandezbottaro.vinculovial.presentation.scan

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.felaufernandezbottaro.vinculovial.data.edge.SecureBitmap
import ar.felaufernandezbottaro.vinculovial.presentation.camera.SecureScreen
import ar.felaufernandezbottaro.vinculovial.presentation.camera.awaitCameraProviderInternal
import ar.felaufernandezbottaro.vinculovial.presentation.camera.captureRawJpegInternal
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvCircleIconButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.evidence.GalleryEvidenceSource
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Captura guiada de la evidencia, paso a paso: cámara en vivo con la instrucción del paso, y al
 * sacar la foto una vista previa para repetirla o continuar. Las fotos sólo se toman con la
 * cámara in-app (`ImageCapture` en memoria); en `debug`, [galleryEvidenceSource] suma la opción
 * de elegir de la galería, que en release no existe.
 */
@Composable
fun EvidenceCaptureScreen(
    state: EvidenceCaptureState,
    galleryEvidenceSource: GalleryEvidenceSource,
    decodeReviewPhoto: () -> Bitmap?,
    onPhotoCaptured: (jpeg: ByteArray, rotationDegrees: Int) -> Unit,
    onPhotoCaptureFailed: () -> Unit,
    onPhotoRetake: () -> Unit,
    onAddAnotherPhoto: () -> Unit,
    onStepConfirmed: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraFailure by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    val busy = state.phase == EvidencePhase.PROCESSING
    val step = state.currentStep

    SecureScreen()
    BackHandler(enabled = !busy) { showCancelDialog = true }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember { ImageCapture.Builder().setFlashMode(ImageCapture.FLASH_MODE_OFF).build() }

    LaunchedEffect(Unit) {
        cameraFailure = try {
            val provider = withContext(Dispatchers.Main) { context.awaitCameraProviderInternal() }
            val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }

            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            cameraProvider = provider
            null
        } catch (e: Exception) {
            "No pudimos abrir la cámara. Cerrá otras apps que la estén usando e intentá de nuevo."
        }
    }

    // La key es Unit a propósito: ver el comentario equivalente en LivenessCaptureScreen.
    DisposableEffect(Unit) {
        onDispose { cameraProvider?.unbindAll() }
    }

    // La vista previa se decodifica en memoria desde el buffer; se sobreescribe al reemplazarla
    // o al salir de la pantalla.
    val reviewBitmap by produceState<Bitmap?>(initialValue = null, state.reviewVersion, state.phase) {
        value = if (state.phase == EvidencePhase.REVIEWING) decodeReviewPhoto() else null
    }
    DisposableEffect(reviewBitmap) {
        val shown = reviewBitmap
        onDispose { SecureBitmap.wipe(shown) }
    }

    val pickGalleryImage = galleryEvidenceSource.rememberPickerLauncher { bytes -> onPhotoCaptured(bytes, 0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        step?.overlayHint?.let { hint ->
            if (state.phase == EvidencePhase.CAPTURING) FramingGuide(hint = hint)
        }

        reviewBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Foto sacada",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VvCircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cancelar",
                enabled = !busy,
                onClick = { showCancelDialog = true },
            )

            if (step != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.6f)) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = stepCounter(state),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                        Text(
                            text = step.instruction,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val message = cameraFailure ?: state.error?.message()
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelLarge,
                    color = VvOrange,
                    textAlign = TextAlign.Center,
                )
            }

            when (state.phase) {
                EvidencePhase.CAPTURING -> {
                    ShutterButton(
                        enabled = cameraProvider != null && !capturing,
                        onClick = {
                            capturing = true
                            imageCapture.captureRawJpegInternal(
                                context = context,
                                onJpeg = { jpeg, rotationDegrees ->
                                    capturing = false
                                    onPhotoCaptured(jpeg, rotationDegrees)
                                },
                                onFailure = {
                                    capturing = false
                                    onPhotoCaptureFailed()
                                },
                            )
                        },
                    )
                    pickGalleryImage?.let { launch ->
                        TextButton(onClick = launch) {
                            Text(text = "Elegir de la galería", color = Color.White)
                        }
                    }
                }

                EvidencePhase.PROCESSING -> {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                    Text(text = "Procesando la foto…", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }

                EvidencePhase.REVIEWING -> ReviewActions(
                    state = state,
                    onRetake = onPhotoRetake,
                    onAddAnother = onAddAnotherPhoto,
                    onConfirm = onStepConfirmed,
                )

                EvidencePhase.IDLE, EvidencePhase.COMPLETE -> Unit
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("¿Deseás cancelar el reporte?") },
            text = { Text("Las fotos que sacaste se descartan y no se envía ningún reporte.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onCancel()
                    },
                ) { Text("Sí, cancelar", color = VvOrange) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Seguir", color = VvNavy) }
            },
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
        modifier = Modifier
            .size(72.dp)
            .border(width = 4.dp, color = Color.White.copy(alpha = 0.5f), shape = CircleShape)
            .semantics { contentDescription = "Sacar foto" },
    ) {}
}

@Composable
private fun ReviewActions(
    state: EvidenceCaptureState,
    onRetake: () -> Unit,
    onAddAnother: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                Text("Repetir foto", color = Color.White)
            }
            if (state.canAddAnother) {
                OutlinedButton(onClick = onAddAnother, modifier = Modifier.weight(1f)) {
                    Text("Agregar otra", color = Color.White)
                }
            }
        }
        VvPrimaryButton(
            text = if (state.isLastStep) "Terminar" else "Continuar",
            onClick = onConfirm,
        )
    }
}

/** Marco guía del encuadre de un paso, con la indicación sobre cómo tiene que entrar la escena. */
@Composable
private fun FramingGuide(hint: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width * FRAME_WIDTH_FRACTION
            val height = size.height * FRAME_HEIGHT_FRACTION
            drawRoundRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset((size.width - width) / 2f, size.height * FRAME_TOP_FRACTION),
                size = Size(width, height),
                cornerRadius = CornerRadius(FRAME_CORNER_PX, FRAME_CORNER_PX),
                style = Stroke(width = FRAME_STROKE_PX),
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 40.dp),
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

private fun stepCounter(state: EvidenceCaptureState): String {
    val step = state.currentStep ?: return ""
    val base = "Paso ${state.stepIndex + 1} de ${state.steps.size}"
    return if (step.maxPhotos > 1) "$base · foto ${state.photoIndex + 1} de hasta ${step.maxPhotos}" else base
}

private fun EvidenceError.message(): String = when (this) {
    EvidenceError.CAPTURE_FAILED -> "No pudimos sacar la foto. Intentá de nuevo."
    EvidenceError.UNREADABLE -> "No pudimos leer la imagen. Probá con otra foto."
    EvidenceError.TOO_LARGE -> "La foto es demasiado pesada. Sacala de nuevo."
}

private const val FRAME_WIDTH_FRACTION = 0.86f
private const val FRAME_HEIGHT_FRACTION = 0.34f
private const val FRAME_TOP_FRACTION = 0.42f
private const val FRAME_CORNER_PX = 48f
private const val FRAME_STROKE_PX = 4f
