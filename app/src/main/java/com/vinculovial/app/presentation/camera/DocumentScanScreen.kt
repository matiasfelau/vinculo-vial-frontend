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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
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
import com.vinculovial.app.presentation.theme.VvNavy
import com.vinculovial.app.presentation.theme.VvOrange
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val UNSUPPORTED_DNI_MESSAGE =
    "Para esta fase de pruebas, por favor utiliza un DNI tarjeta tradicional (con código de barras)."

private const val MANUAL_CAPTURE_FAILURE_MESSAGE =
    "No pudimos leer el DNI. Evitá los reflejos y asegurate de que esté nítido."

/**
 * Escaneo del frente del documento, con captura híbrida.
 *
 * El escaneo en vivo (`DocumentScanSession`, sobre el stream de `ImageAnalysis`) intenta
 * detectarlo solo y dispara la foto automáticamente. Pero el autoenfoque, la resolución
 * del preview o un reflejo sobre el plástico pueden hacer que nunca lo logre en cierto
 * hardware — es un problema conocido de fragmentación en Android, no algo que se pueda
 * afinar para todos los dispositivos. Por eso hay un botón obturador manual: toma una
 * foto de resolución completa con `ImageCapture` y la valida en el momento contra el
 * mismo pipeline real (PDF417 + detección de cara), sin esperar al escaneo en vivo.
 *
 * Los dos caminos comparten el mismo lock (`capturing`): cualquiera que dispare primero
 * bloquea al otro, así nunca se toman dos fotos a la vez.
 *
 * Por ahora la app sólo acepta el DNI actual (PDF417 en el mismo frente): si ninguno de
 * los dos caminos encuentra el código, se avisa con un Toast y se reinicia el escaneo en
 * vivo — no se le pide el reverso.
 *
 * @param onManualCapture valida una foto tomada manualmente contra el pipeline real. No
 * la acepta: eso lo hace [onScanned] si el resultado da bien.
 * @param onScanned entrega la foto del frente, por cualquiera de los dos caminos. Toma
 * posesión de los bytes.
 */
@Composable
fun DocumentScanScreen(
    documentScanSession: DocumentScanSession,
    onManualCapture: suspend (image: ByteArray) -> Boolean,
    onScanned: (frontImage: ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val progress by documentScanSession.progress.collectAsStateWithLifecycle()

    // Compartido por el camino automático y el manual: el que dispare primero pone esto
    // en true y bloquea al otro, así nunca se dispara una captura encima de otra.
    var capturing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var showCancelDialog by remember { mutableStateOf(false) }

    val locked = capturing || progress.outcome == DocumentScanOutcome.FOUND

    // Siempre intercepta (no se deshabilita el handler): mientras se puede cancelar,
    // pide confirmación; una vez en curso la captura, directamente se ignora — no cae al
    // back por defecto de la navegación, que saldría de la pantalla sin avisar.
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

    // La key es Unit a propósito, NO `cameraProvider`: ver el comentario equivalente en
    // LivenessCaptureScreen — keyear con un estado que el propio efecto escribe hace que
    // el bind se desvincule solo, un instante después de bindear.
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
            documentScanSession.close()
        }
    }

    // El escaneo en vivo decide solo; acá sólo se reacciona a lo que ya decidió.
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
                // documento o reacomoda el que tiene. El botón manual sigue disponible
                // durante todo este tiempo, sin depender de este timeout.
                documentScanSession.start()
            }

            DocumentScanOutcome.NONE -> Unit
        }
    }

    // Fallback manual: toma una foto de resolución completa y la valida en el momento
    // contra el pipeline real, sin esperar (ni depender de) el resultado del escaneo en
    // vivo, que puede seguir corriendo en paralelo sin interferir.
    fun captureManually() {
        if (capturing) return
        capturing = true
        imageCapture.captureJpegInternal(
            context = context,
            onJpeg = { bytes ->
                coroutineScope.launch {
                    val readable = try {
                        onManualCapture(bytes)
                    } catch (e: Exception) {
                        false
                    }
                    if (readable) {
                        onScanned(bytes)
                    } else {
                        capturing = false
                        Toast.makeText(context, MANUAL_CAPTURE_FAILURE_MESSAGE, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onFailure = {
                capturing = false
                failure = "No pudimos tomar la foto. Intentá de nuevo."
            },
        )
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
                .padding(horizontal = 28.dp, vertical = 28.dp),
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

            // Se reemplaza por el spinner de arriba ni bien hay una captura en curso,
            // en vez de quedar deshabilitado pero visible.
            if (!locked) {
                ManualCaptureShutterButton(onClick = ::captureManually)
            }
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

/**
 * Obturador manual, estilo cámara: siempre disponible como alternativa al escaneo en
 * vivo, no sólo tras un timeout — es el fallback estándar en flujos KYC contra la
 * fragmentación de hardware (autoenfoque, resolución, reflejos), no un último recurso.
 */
@Composable
private fun ManualCaptureShutterButton(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.White,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = "Capturar DNI",
                tint = VvNavy,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
            )
        }
        Text(
            text = "Capturar DNI",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
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
