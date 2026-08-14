package com.vinculovial.app.presentation.camera

import android.content.Context
import android.graphics.ImageFormat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vinculovial.app.presentation.components.VvCircleIconButton
import com.vinculovial.app.presentation.theme.VvOrange
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Captura una foto con la cámara del dispositivo y la entrega **en memoria**.
 *
 * Se usa `ImageCapture.takePicture(executor, OnImageCapturedCallback)` a propósito, en
 * vez de `ActivityResultContracts.TakePicture`: ese contrato exige una URI de destino, o
 * sea que la foto queda escrita en el almacenamiento y visible para la app de cámara.
 * Eso rompería Zero Retention. Acá los bytes salen del buffer de la cámara y van directo
 * a `SecureImageBuffer`, sin pasar por disco.
 *
 * @param onCaptured recibe los bytes JPEG y **toma posesión** de ellos.
 */
@Composable
fun CameraCaptureScreen(
    spec: CameraCaptureSpec,
    onCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var capturing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember(spec.useFrontLens) {
        ImageCapture.Builder()
            // Un documento mal comprimido no se puede leer: prioriza calidad sobre latencia.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()
    }

    LaunchedEffect(spec.useFrontLens) {
        failure = try {
            val provider = context.awaitCameraProvider()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = if (spec.useFrontLens) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            cameraProvider = provider
            null
        } catch (e: Exception) {
            "No pudimos abrir la cámara. Cerrá otras apps que la estén usando e intentá de nuevo."
        }
    }

    // Al salir de la pantalla no queda ningún stream de cámara vivo por detrás.
    DisposableEffect(cameraProvider) {
        onDispose { cameraProvider?.unbindAll() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        CaptureGuideOverlay(guide = spec.guide)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            VvCircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cancelar captura",
                onClick = onCancel,
                enabled = !capturing,
            )
            Text(
                text = spec.title,
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
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = failure ?: spec.instruction,
                style = MaterialTheme.typography.bodyLarge,
                color = if (failure != null) VvOrange else Color.White,
                textAlign = TextAlign.Center,
            )

            ShutterButton(
                enabled = !capturing && failure == null,
                capturing = capturing,
                onClick = {
                    capturing = true
                    imageCapture.captureJpeg(
                        context = context,
                        onJpeg = { bytes ->
                            capturing = false
                            onCaptured(bytes)
                        },
                        onFailure = {
                            capturing = false
                            failure = "No pudimos tomar la foto. Intentá de nuevo."
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, capturing: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.White,
        modifier = Modifier.size(76.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (capturing) {
                CircularProgressIndicator(
                    color = Color.Black,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** Oscurece la pantalla salvo el recuadro donde tiene que entrar el documento o el rostro. */
@Composable
private fun CaptureGuideOverlay(guide: CaptureGuide) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            // Necesario para que BlendMode.Clear recorte sobre el velo y no sobre la pantalla.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        drawRect(Color.Black.copy(alpha = 0.55f))

        when (guide) {
            CaptureGuide.DOCUMENT -> {
                // Proporción de tarjeta ID-1 (85.6 x 54 mm), la del DNI.
                val width = size.width * 0.86f
                val height = width * (54f / 85.6f)
                val topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2f)
                val area = Size(width, height)
                val corner = CornerRadius(20f, 20f)

                drawRoundRect(Color.Transparent, topLeft, area, corner, blendMode = BlendMode.Clear)
                drawRoundRect(Color.White, topLeft, area, corner, style = Stroke(width = 4f))
            }

            CaptureGuide.FACE -> {
                val width = size.width * 0.68f
                val height = width * 1.35f
                val topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2f)
                val area = Size(width, height)

                drawOval(Color.Transparent, topLeft, area, blendMode = BlendMode.Clear)
                drawOval(Color.White, topLeft, area, style = Stroke(width = 4f))
            }
        }
    }
}

/**
 * Toma la foto y devuelve el JPEG. El [ImageProxy] se cierra siempre, así el buffer
 * nativo de la cámara se libera aunque falle la conversión.
 */
private fun ImageCapture.captureJpeg(
    context: Context,
    onJpeg: (ByteArray) -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {

            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    image.use { proxy ->
                        if (proxy.format != ImageFormat.JPEG) {
                            onFailure(IllegalStateException("Formato inesperado: ${proxy.format}"))
                        } else {
                            onJpeg(proxy.readJpegBytes())
                        }
                    }
                } catch (e: Exception) {
                    onFailure(e)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onFailure(exception)
            }
        },
    )
}

/** Para un JPEG, el primer plano contiene la imagen completa ya comprimida. */
private fun ImageProxy.readJpegBytes(): ByteArray {
    val buffer = planes[0].buffer
    return ByteArray(buffer.remaining()).also(buffer::get)
}

/** `ProcessCameraProvider.getInstance` expone un `ListenableFuture`; acá se espera sin bloquear. */
private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }
