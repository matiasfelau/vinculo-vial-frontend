package com.vinculovial.app.presentation.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinculovial.app.domain.signup.DocumentScanSession
import com.vinculovial.app.domain.signup.model.DocumentScanMode
import com.vinculovial.app.domain.signup.model.DocumentScanOutcome
import com.vinculovial.app.domain.signup.model.DocumentScanProgress
import com.vinculovial.app.domain.signup.model.FramingHint
import com.vinculovial.app.presentation.components.VvCircleIconButton
import com.vinculovial.app.presentation.theme.VvGreen
import com.vinculovial.app.presentation.theme.VvOrange
import java.util.concurrent.Executors

/**
 * Escaneo del documento: frente primero, reverso sólo si hace falta.
 *
 * En Argentina conviven dos formatos de DNI y esta pantalla no le pregunta al usuario
 * cuál tiene — lo decide sola mirando el frente:
 * - si encuentra la cara **y** un código de barras al mismo tiempo → DNI actual, listo;
 * - si encuentra la cara pero **no** un código → DNI electrónico, pide el reverso.
 *
 * Ambas fases usan la cámara trasera (nunca la frontal: acá no hay selfie, sólo se
 * fotografía el documento) y no hace falta volver a pedir el permiso ni rebindear la
 * cámara entre una y otra — sólo cambia el modo del [DocumentScanSession] y el texto en
 * pantalla.
 *
 * @param onScanned entrega el frente siempre, y el reverso sólo si se llegó a escanear.
 * Toma posesión de los bytes.
 */
@Composable
fun DocumentScanScreen(
    documentScanSession: DocumentScanSession,
    onScanned: (frontImage: ByteArray, backImage: ByteArray?) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val progress by documentScanSession.progress.collectAsStateWithLifecycle()

    var phase by remember { mutableStateOf<ScanPhase>(ScanPhase.Front) }
    var capturing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // Ver LivenessCaptureScreen: SurfaceView (el modo por defecto) puede quedar
            // negro cuando hay otro contenido de Compose dibujado encima, en hardware real.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            // Un documento mal comprimido no se puede leer, ni el código ni la cara.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(Unit) {
        documentScanSession.start(DocumentScanMode.FACE_AND_BARCODE)

        failure = try {
            val provider = context.awaitCameraProviderInternal()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(analysisExecutor) { imageProxy ->
                        val frame = imageProxy.toCameraFrame()
                        imageProxy.close()
                        if (frame != null) {
                            kotlinx.coroutines.runBlocking { documentScanSession.submitFrame(frame) }
                        }
                    }
                }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
                imageCapture,
            )
            cameraProvider = provider
            null
        } catch (e: Exception) {
            "No pudimos abrir la cámara. Cerrá otras apps que la estén usando e intentá de nuevo."
        }
    }

    DisposableEffect(cameraProvider) {
        onDispose {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
            documentScanSession.close()
        }
    }

    // El escaneo decide solo; acá sólo se reacciona a lo que ya decidió.
    LaunchedEffect(progress.outcome) {
        if (capturing) return@LaunchedEffect

        when (progress.outcome) {
            DocumentScanOutcome.LEGACY_WITH_BARCODE -> {
                capturing = true
                imageCapture.captureJpegInternal(
                    context = context,
                    onJpeg = { front -> onScanned(front, null) },
                    onFailure = {
                        capturing = false
                        failure = "No pudimos tomar la foto. Intentá de nuevo."
                    },
                )
            }

            DocumentScanOutcome.ELECTRONIC_NO_BARCODE -> {
                capturing = true
                imageCapture.captureJpegInternal(
                    context = context,
                    onJpeg = { front ->
                        capturing = false
                        phase = ScanPhase.Back(frontImage = front)
                        documentScanSession.start(DocumentScanMode.BARCODE_ONLY)
                    },
                    onFailure = {
                        capturing = false
                        failure = "No pudimos tomar la foto. Intentá de nuevo."
                    },
                )
            }

            DocumentScanOutcome.BARCODE_FOUND -> {
                val current = phase as? ScanPhase.Back ?: return@LaunchedEffect
                capturing = true
                imageCapture.captureJpegInternal(
                    context = context,
                    onJpeg = { back -> onScanned(current.frontImage, back) },
                    onFailure = {
                        capturing = false
                        failure = "No pudimos tomar la foto. Intentá de nuevo."
                    },
                )
            }

            DocumentScanOutcome.NONE -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        DocumentGuideOverlay(highlighted = progress.barcodeDetected)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            VvCircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cancelar",
                onClick = onCancel,
                enabled = !capturing,
            )
            Text(
                text = phase.title(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (capturing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            }

            Text(
                text = failure ?: progress.instructionText(phase),
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    failure != null -> VvOrange
                    progress.isDecided -> VvGreen
                    else -> Color.White
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Qué foto está esperando la pantalla. El reverso sólo existe si el frente resultó ser
 * un DNI electrónico — por eso lleva la foto del frente adentro, no como variable aparte
 * que pudiera estar vacía en el momento equivocado.
 */
private sealed interface ScanPhase {
    data object Front : ScanPhase
    data class Back(val frontImage: ByteArray) : ScanPhase
}

private fun ScanPhase.title(): String = when (this) {
    ScanPhase.Front -> "Frente del DNI"
    is ScanPhase.Back -> "Reverso del DNI"
}

private fun DocumentScanProgress.instructionText(phase: ScanPhase): String = when {
    hint == FramingHint.MULTIPLE_FACES -> "Que sólo se vea el documento"
    hint == FramingHint.TOO_FAR -> "Acercate un poco"
    hint == FramingHint.TOO_CLOSE -> "Alejate un poco"
    hint == FramingHint.NO_FACE && phase is ScanPhase.Front ->
        "Encuadrá el frente del documento, con tu foto visible"

    phase is ScanPhase.Back && barcodeDetected -> "¡Ya casi! Mantené el código enfocado"
    phase is ScanPhase.Back -> "Dá vuelta tu DNI para escanear el código del reverso"
    barcodeDetected -> "¡Perfecto! Mantené el documento quieto"
    else -> "Encuadrá el frente de tu DNI dentro del recuadro"
}

/** Rectángulo con proporción de tarjeta (ID-1), para encuadrar el documento. */
@Composable
private fun DocumentGuideOverlay(highlighted: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            // Necesario para que BlendMode.Clear recorte sobre el velo y no la pantalla.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        drawRect(Color.Black.copy(alpha = 0.55f))

        // Proporción de tarjeta ID-1 (85.6 x 54 mm), la del DNI.
        val width = size.width * 0.86f
        val height = width * (54f / 85.6f)
        val topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2f)
        val area = Size(width, height)
        val corner = CornerRadius(20f, 20f)

        drawRoundRect(Color.Transparent, topLeft, area, corner, blendMode = BlendMode.Clear)
        drawRoundRect(
            color = lerp(Color.White, VvGreen, if (highlighted) 1f else 0f),
            topLeft = topLeft,
            size = area,
            cornerRadius = corner,
            style = Stroke(width = 4f),
        )
    }
}
