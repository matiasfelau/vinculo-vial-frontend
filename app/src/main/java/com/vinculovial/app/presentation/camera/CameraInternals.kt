package com.vinculovial.app.presentation.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

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

/** Para un JPEG, el primer plano contiene la imagen completa ya comprimida. */
private fun ImageProxy.readJpegBytesInternal(): ByteArray {
    val buffer = planes[0].buffer
    return ByteArray(buffer.remaining()).also(buffer::get)
}

/**
 * Decodifica, rota a vertical, la reduce a un tamaño manejable y vuelve a codificar en JPEG.
 *
 * El downscale corre siempre, no sólo cuando hace falta rotar: una foto sacada a pulso, de
 * cerca y con buena luz, tiene resolución de sobra para que las líneas de seguridad del DNI
 * generen moiré sobre el PDF417 y lo tapen — bajar la resolución antes de pasarla a ML Kit
 * saca ese ruido de alta frecuencia sin perder legibilidad. Ni el código de barras ni la cara
 * necesitan más que esto: el embedding facial en sí trabaja sobre 112x112.
 *
 * Se rotan los píxeles en vez de pasarle el ángulo a `InputImage.fromBitmap(bitmap, deg)`
 * a propósito: ese parámetro tiene reportes conocidos de comportarse de forma inconsistente
 * en detección de caras/texto según el modelo de ML Kit. Rotar acá deja el bitmap
 * genuinamente vertical, así el resto del pipeline siempre pasa rotación 0 y no depende
 * de esa API.
 */
private fun ByteArray.rotatedJpeg(rotationDegrees: Int): ByteArray {
    val original = BitmapFactory.decodeByteArray(this, 0, size) ?: return this

    val upright = if (rotationDegrees == 0) {
        original
    } else {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            .also { original.recycle() }
    }

    val resized = upright.downscaledInternal(MAX_DIMENSION_PX)

    return try {
        ByteArrayOutputStream().use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    } finally {
        if (resized !== upright) resized.recycle()
        upright.recycle()
    }
}

/** Nunca agranda: sólo achica cuando el lado más largo supera el máximo. */
private fun Bitmap.downscaledInternal(maxDimensionPx: Int): Bitmap {
    val largestSide = maxOf(width, height)
    if (largestSide <= maxDimensionPx) return this

    val scaleFactor = maxDimensionPx.toFloat() / largestSide
    val targetWidth = (width * scaleFactor).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scaleFactor).roundToInt().coerceAtLeast(1)
    return scale(targetWidth, targetHeight)
}

private const val JPEG_QUALITY = 95

// Alcanza y sobra tanto para que ML Kit lea el PDF417 como para recortar una cara: el
// embedding en sí termina reduciendo ese recorte a 112x112 igual.
private const val MAX_DIMENSION_PX = 1000

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
