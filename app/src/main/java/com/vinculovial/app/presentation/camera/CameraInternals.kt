package com.vinculovial.app.presentation.camera

import android.content.Context
import android.graphics.ImageFormat
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Piezas de CameraX compartidas por la captura de documento y la de selfie con liveness.
 *
 * Ambas entregan la foto **en memoria**: se usa
 * `takePicture(executor, OnImageCapturedCallback)` y no
 * `ActivityResultContracts.TakePicture`, porque ese contrato exige una URI de destino y
 * dejaría la imagen escrita en el almacenamiento, rompiendo Zero Retention.
 */
internal fun ImageCapture.captureJpegInternal(
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
                            onJpeg(proxy.readJpegBytesInternal())
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
internal fun ImageProxy.readJpegBytesInternal(): ByteArray {
    val buffer = planes[0].buffer
    return ByteArray(buffer.remaining()).also(buffer::get)
}

/** `ProcessCameraProvider.getInstance` expone un `ListenableFuture`; acá se espera sin bloquear. */
internal suspend fun Context.awaitCameraProviderInternal(): ProcessCameraProvider =
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
