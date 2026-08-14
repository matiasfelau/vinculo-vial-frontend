package com.vinculovial.app.presentation.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinculovial.app.domain.signup.LivenessSession
import com.vinculovial.app.domain.signup.model.LivenessChallenge
import com.vinculovial.app.domain.signup.model.LivenessHint
import com.vinculovial.app.domain.signup.model.LivenessStage
import com.vinculovial.app.presentation.components.VvCircleIconButton
import com.vinculovial.app.presentation.theme.VvGreen
import com.vinculovial.app.presentation.theme.VvOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Selfie con prueba de vida activa.
 *
 * A diferencia de la captura de documento, acá no hay obturador: el preview se analiza
 * en vivo y la foto se toma **sola** cuando el usuario completa el gesto. Es lo que hace
 * que la foto entregada corresponda al momento en que se probó la presencia.
 */
@Composable
fun LivenessCaptureScreen(
    livenessSession: LivenessSession,
    onCaptured: (image: ByteArray, livenessPassed: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val progress by livenessSession.progress.collectAsStateWithLifecycle()

    var capturing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember { ImageCapture.Builder().setFlashMode(ImageCapture.FLASH_MODE_OFF).build() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(Unit) {
        livenessSession.start()

        failure = try {
            val provider = context.awaitCameraProviderForLiveness()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                // Interesa el frame más reciente; acumular retrasaría el desafío.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(analysisExecutor) { imageProxy ->
                        val frame = imageProxy.toLivenessFrame()
                        // Cerrar el proxy antes de analizar libera el slot de la cámara
                        // enseguida; el frame ya está copiado a NV21.
                        imageProxy.close()
                        if (frame != null) {
                            // El analyzer corre en su propio hilo y CameraX espera que sea
                            // bloqueante. submitFrame descarta el frame si hay otro en
                            // curso, así que no se acumula trabajo.
                            kotlinx.coroutines.runBlocking { livenessSession.submitFrame(frame) }
                        }
                    }
                }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
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

    // Al superar el desafío se dispara la foto sin intervención del usuario.
    LaunchedEffect(progress.stage) {
        if (progress.stage == LivenessStage.PASSED && !capturing) {
            capturing = true
            imageCapture.captureJpegForLiveness(
                context = context,
                onJpeg = { bytes -> onCaptured(bytes, true) },
                onFailure = {
                    capturing = false
                    failure = "No pudimos tomar la foto. Intentá de nuevo."
                },
            )
        }
    }

    DisposableEffect(cameraProvider) {
        onDispose {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
            livenessSession.close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        FaceOvalOverlay(passed = progress.stage == LivenessStage.PASSED)

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
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (capturing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            }

            Text(
                text = failure ?: progress.instructionText(),
                style = MaterialTheme.typography.headlineMedium,
                color = when {
                    failure != null -> VvOrange
                    progress.stage == LivenessStage.PASSED -> VvGreen
                    else -> Color.White
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Óvalo que se pone verde al superar el desafío. */
@Composable
private fun FaceOvalOverlay(passed: Boolean) {
    val strokeColor by animateFloatAsState(
        targetValue = if (passed) 1f else 0f,
        animationSpec = tween(220),
        label = "liveness-oval",
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        drawRect(Color.Black.copy(alpha = 0.55f))

        val width = size.width * 0.68f
        val height = width * 1.35f
        val topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2.4f)
        val area = Size(width, height)

        drawOval(Color.Transparent, topLeft, area, blendMode = BlendMode.Clear)
        drawOval(
            color = androidx.compose.ui.graphics.lerp(Color.White, VvGreen, strokeColor),
            topLeft = topLeft,
            size = area,
            style = Stroke(width = 6f),
        )
    }
}

/** Qué se le dice al usuario en cada punto del desafío. */
private fun com.vinculovial.app.domain.signup.model.LivenessProgress.instructionText(): String =
    when {
        hint == LivenessHint.NO_FACE -> "Poné tu cara dentro del óvalo"
        hint == LivenessHint.MULTIPLE_FACES -> "Que sólo se vea tu cara"
        hint == LivenessHint.TOO_FAR -> "Acercate un poco"
        hint == LivenessHint.TOO_CLOSE -> "Alejate un poco"
        hint == LivenessHint.LOOK_STRAIGHT -> "Mirá de frente a la cámara"
        stage == LivenessStage.PASSED -> "¡Listo!"
        stage == LivenessStage.WAITING_GESTURE -> when (challenge) {
            LivenessChallenge.SMILE -> "Ahora sonreí"
            LivenessChallenge.BLINK -> "Ahora parpadeá"
        }
        else -> when (challenge) {
            LivenessChallenge.SMILE -> "Mantené cara neutra un segundo"
            LivenessChallenge.BLINK -> "Mantené los ojos abiertos un segundo"
        }
    }

private fun ImageCapture.captureJpegForLiveness(
    context: Context,
    onJpeg: (ByteArray) -> Unit,
    onFailure: (Throwable) -> Unit,
) = captureJpegInternal(context, onJpeg, onFailure)

private suspend fun Context.awaitCameraProviderForLiveness(): ProcessCameraProvider =
    withContext(Dispatchers.Main) { awaitCameraProviderInternal() }
