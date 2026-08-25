package ar.felaufernandezbottaro.vinculovial.data.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import ar.felaufernandezbottaro.vinculovial.domain.scan.model.OverlayRect
import ar.felaufernandezbottaro.vinculovial.presentation.camera.toCameraFrame
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Recorta el frame de la cámara al rectángulo del overlay ANTES de correr reconocimiento de
 * texto. No es cosmético: reduce el ruido de fondo, la causa principal de falsos positivos
 * en OCR de texto libre (ver CLAUDE.md).
 *
 * El recorte se hace sobre el JPEG (`YuvImage.compressToJpeg` acepta un `Rect` de origen), así
 * nunca se materializa un bitmap del frame completo -- sólo de la región de interés.
 */
class FrameCropper @Inject constructor() {

    fun crop(imageProxy: ImageProxy, overlay: OverlayRect = OverlayRect.PLATE): Bitmap? {
        val frame = imageProxy.toCameraFrame() ?: return null
        val rect = cropRect(frame.width, frame.height, frame.rotationDegrees, overlay)
        if (rect.width <= 0 || rect.height <= 0) return null

        val yuvImage = YuvImage(frame.nv21, ImageFormat.NV21, frame.width, frame.height, null)
        val jpeg = ByteArrayOutputStream().use { out ->
            val ok = yuvImage.compressToJpeg(Rect(rect.left, rect.top, rect.right, rect.bottom), JPEG_QUALITY, out)
            if (!ok) return null
            out.toByteArray()
        }

        val cropped = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        if (frame.rotationDegrees == 0) return cropped

        val matrix = Matrix().apply { postRotate(frame.rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
            .also { upright -> if (upright !== cropped) cropped.recycle() }
    }

    private companion object {
        const val JPEG_QUALITY = 92
    }
}

/** Rectángulo de píxeles en coordenadas del buffer, sin depender de `android.graphics.Rect`. */
internal data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Mapea el rectángulo del overlay -- expresado como fracción del frame ya vertical -- a
 * coordenadas de píxel del buffer tal como lo entrega el sensor, antes de rotar.
 *
 * Rotar un rectángulo entero (no un punto) puede invertir qué esquina queda arriba a la
 * izquierda, así que se mapean las cuatro esquinas del rectángulo vertical y se toma el
 * rectángulo delimitador resultante en espacio del sensor -- no alcanza con rotar sólo
 * left/top. Es pura aritmética de enteros: no depende de ninguna clase de Android, por eso
 * es testeable sin Robolectric.
 */
internal fun cropRect(sensorWidth: Int, sensorHeight: Int, rotationDegrees: Int, overlay: OverlayRect): PixelRect {
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    val uprightWidth: Int
    val uprightHeight: Int
    if (normalizedRotation == 90 || normalizedRotation == 270) {
        uprightWidth = sensorHeight
        uprightHeight = sensorWidth
    } else {
        uprightWidth = sensorWidth
        uprightHeight = sensorHeight
    }

    val uLeft = (overlay.leftFraction * uprightWidth).roundToInt()
    val uTop = (overlay.topFraction * uprightHeight).roundToInt()
    val uRight = uLeft + (overlay.widthFraction * uprightWidth).roundToInt()
    val uBottom = uTop + (overlay.heightFraction * uprightHeight).roundToInt()

    val corners = listOf(uLeft to uTop, uRight to uTop, uLeft to uBottom, uRight to uBottom)
        .map { (x, y) -> mapUprightToSensor(x, y, sensorWidth, sensorHeight, normalizedRotation) }

    val left = corners.minOf { it.first }.coerceIn(0, sensorWidth)
    val right = corners.maxOf { it.first }.coerceIn(0, sensorWidth)
    val top = corners.minOf { it.second }.coerceIn(0, sensorHeight)
    val bottom = corners.maxOf { it.second }.coerceIn(0, sensorHeight)

    return PixelRect(left, top, right, bottom)
}

/**
 * Inversa de "rotar el buffer `rotationDegrees` en sentido horario para dejarlo vertical":
 * dado un punto ya en coordenadas verticales, devuelve dónde estaba en el buffer del sensor.
 * `sensorWidth`/`sensorHeight` son siempre las dimensiones ORIGINALES del sensor (antes de
 * rotar), sea cual sea la rotación.
 */
private fun mapUprightToSensor(
    x: Int,
    y: Int,
    sensorWidth: Int,
    sensorHeight: Int,
    normalizedRotationDegrees: Int,
): Pair<Int, Int> = when (normalizedRotationDegrees) {
    90 -> y to (sensorHeight - 1 - x)
    180 -> (sensorWidth - 1 - x) to (sensorHeight - 1 - y)
    270 -> (sensorWidth - 1 - y) to x
    else -> x to y
}
