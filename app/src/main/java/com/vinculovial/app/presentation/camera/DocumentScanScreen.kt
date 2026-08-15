package com.vinculovial.app.presentation.camera

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import com.vinculovial.app.domain.signup.model.DocumentScanOutcome
import com.vinculovial.app.domain.signup.model.DocumentScanProgress
import com.vinculovial.app.domain.signup.model.FramingHint
import com.vinculovial.app.presentation.components.VvCircleIconButton
import com.vinculovial.app.presentation.theme.VvGreen
import com.vinculovial.app.presentation.theme.VvOrange
import java.util.concurrent.Executors

private const val UNSUPPORTED_DNI_MESSAGE =
    "Para esta fase de pruebas, por favor utiliza un DNI tarjeta tradicional (con código de barras)."

/**
 * Escaneo del frente del documento.
 *
 * Por ahora la app sólo acepta el DNI actual (PDF417 en el mismo frente): si el escaneo
 * en vivo confirma que hay cara pero nunca aparece el código, se avisa con un Toast y se
 * reinicia el escaneo — no se le pide el reverso.
 *
 * @param onScanned entrega la foto del frente. Toma posesión de los bytes.
 */
@Composable
fun DocumentScanScreen(
    documentScanSession: DocumentScanSession,
    onScanned: (frontImage: ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val progress by documentScanSession.progress.collectAsStateWithLifecycle()

    var capturing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var showCancelDialog by remember { mutableStateOf(false) }

    // Se calcula ni bien el escaneo decide que hay código, no recién cuando arranca la
    // captura asíncrona: así no queda ni un frame en el que el botón de cancelar siga
    // habilitado o el spinner tarde en aparecer.
    val locked = capturing || progress.outcome == DocumentScanOutcome.FOUND

    // Atrás cancela con confirmación mientras se puede cancelar; una vez decidido el
    // documento, se ignora — no tiene sentido interrumpir una captura que ya está en curso.
    BackHandler {
        if (!locked) showCancelDialog = true
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // SurfaceView (el modo por defecto) puede quedar negro cuando hay otro
            // contenido de Compose dibujado encima, en hardware real.
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
        documentScanSession.start()

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
                        // El try/finally garantiza el close() incluso si toCameraFrame()
                        // lanzara una excepción: sin él, CameraX se queda sin buffers a
                        // los pocos frames. Cerrar ANTES de analizar libera el slot de
                        // la cámara enseguida; el frame ya está copiado a NV21.
                        val frame = try {
                            imageProxy.toCameraFrame()
                        } finally {
                            imageProxy.close()
                        }
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

    // La key es Unit a propósito, NO `cameraProvider`: ese estado se escribe DENTRO del
    // LaunchedEffect de arriba, recién al terminar el bind. Si el efecto se keyea con ese
    // mismo estado, el cambio de null -> provider hace que DisposableEffect trate ese
    // cambio como "la key cambió": dispone el efecto anterior (ejecutando este mismo
    // onDispose, que para entonces ya lee el provider recién bindeado) y lo desvincula
    // in situ, un instante después de bindearlo. Eso es lo que se veía como "la pantalla
    // se pone negra justo cuando aparece el overlay": el bind y el auto-unbind caen en la
    // misma recomposición, y el overlay se redibuja en ese mismo momento porque toda la
    // pantalla recompone — no porque el overlay tenga nada que ver con la cámara.
    DisposableEffect(Unit) {
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
            DocumentScanOutcome.FOUND -> {
                capturing = true
                imageCapture.captureJpegInternal(
                    context = context,
                    onJpeg = { front -> onScanned(front) },
                    onFailure = {
                        capturing = false
                        failure = "No pudimos tomar la foto. Intentá de nuevo."
                    },
                )
            }

            DocumentScanOutcome.NOT_FOUND_TIMEOUT -> {
                Toast.makeText(context, UNSUPPORTED_DNI_MESSAGE, Toast.LENGTH_LONG).show()
                // No es un error de cámara ni del usuario: se reinicia por si cambia de
                // documento o reacomoda el que tiene.
                documentScanSession.start()
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
                onClick = { showCancelDialog = true },
                enabled = !locked,
            )
            Text(
                text = "Frente del DNI",
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
            if (locked) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            }

            Text(
                text = failure ?: progress.instructionText(),
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    failure != null -> VvOrange
                    progress.outcome == DocumentScanOutcome.FOUND -> VvGreen
                    else -> Color.White
                },
                textAlign = TextAlign.Center,
            )
        }

        if (showCancelDialog) {
            CancelRegistrationDialog(
                onConfirm = {
                    showCancelDialog = false
                    onCancel()
                },
                onDismiss = { showCancelDialog = false },
            )
        }
    }
}

private fun DocumentScanProgress.instructionText(): String = when {
    hint == FramingHint.MULTIPLE_FACES -> "Que sólo se vea el documento"
    hint == FramingHint.TOO_FAR -> "Acercate un poco"
    hint == FramingHint.TOO_CLOSE -> "Alejate un poco"
    hint == FramingHint.NO_FACE -> "Encuadrá el frente del documento, con tu foto visible"
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
