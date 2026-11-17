package ar.felaufernandezbottaro.vinculovial.presentation.camera

import android.content.Context
import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import ar.felaufernandezbottaro.vinculovial.BuildConfig
import ar.felaufernandezbottaro.vinculovial.ExcludeFromCoverage
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
@ExcludeFromCoverage(reason = "CameraX ImageCapture: necesita cámara real; la rotación y el submuestreo del JPEG viven en JpegOrientation, que sí se prueba")
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
                            // Diagnóstico de resolución real, no de contenido: confirma si
                            // ImageCapture entregó la resolución del sensor o si el hardware
                            // (u otro stream concurrente bindeado a la vez) la degradó — es la
                            // única forma de distinguir esos dos casos sin acceso físico al
                            // dispositivo. Ver el KDoc de DocumentScanScreen.switchToCaptureMode.
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "ImageCapture entregó ${proxy.width}x${proxy.height}")
                            }

                            // OnImageCapturedCallback entrega el JPEG SIN rotar: el
                            // sensor suele estar montado apaisado, y lo que dice cuánto
                            // rotar para que quede vertical es imageInfo.rotationDegrees,
                            // no el contenido del archivo (acá no hay garantía de EXIF,
                            // a diferencia de la variante que guarda a un OutputFileOptions).
                            // Se corrige acá, una única vez: de acá en más todo el
                            // pipeline (código de barras, cara) trabaja sobre una imagen
                            // ya vertical, sin que nadie más tenga que pensarlo.
                            val upright = proxy.readJpegBytesInternal()
                                .rotatedJpeg(proxy.imageInfo.rotationDegrees)
                            onJpeg(upright)
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

/**
 * Foto en memoria para la evidencia de alertas: entrega el JPEG **tal cual lo captura el
 * sensor**, sin rotar ni recomprimir, junto con los grados que hay que rotarlo. El procesamiento
 * de evidencia (`EvidenceImageProcessor`) aplica la rotación, reescala y re-codifica de una
 * sola vez, en vez de recomprimir dos veces como hace [captureJpegInternal].
 *
 * Sigue siendo `OnImageCapturedCallback`: nada se escribe a archivo, MediaStore ni caché.
 */
@ExcludeFromCoverage(reason = "CameraX ImageCapture: necesita cámara real")
internal fun ImageCapture.captureRawJpegInternal(
    context: Context,
    onJpeg: (jpeg: ByteArray, rotationDegrees: Int) -> Unit,
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
                            onJpeg(proxy.readJpegBytesInternal(), proxy.imageInfo.rotationDegrees)
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

private const val TAG = "CameraCapture"

/** Para un JPEG, el primer plano contiene la imagen completa ya comprimida. */
@ExcludeFromCoverage(reason = "Lee el buffer de un ImageProxy de CameraX: necesita cámara real")
private fun ImageProxy.readJpegBytesInternal(): ByteArray {
    val buffer = planes[0].buffer
    return ByteArray(buffer.remaining()).also(buffer::get)
}

/** `ProcessCameraProvider.getInstance` expone un `ListenableFuture`; acá se espera sin bloquear. */
@ExcludeFromCoverage(reason = "ProcessCameraProvider de CameraX: necesita cámara real")
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
