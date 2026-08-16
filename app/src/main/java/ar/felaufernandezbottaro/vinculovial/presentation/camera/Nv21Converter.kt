package ar.felaufernandezbottaro.vinculovial.presentation.camera

import androidx.camera.core.ImageProxy
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CameraFrame

/**
 * Convierte un frame YUV_420_888 de CameraX a NV21, que es el formato neutral que
 * atraviesa la frontera hacia `domain`. La usan tanto la prueba de vida como el
 * escaneo de documento: ambas analizan el stream, no una foto sola.
 *
 * Es una copia por frame, pero el análisis está limitado a ~8 fps, así que el costo es
 * marginal frente a la detección en sí (facial o de código de barras).
 */
internal fun ImageProxy.toCameraFrame(): CameraFrame? {
    if (planes.size < 3) return null

    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val ySize = width * height
    val uvSize = ySize / 2
    val nv21 = ByteArray(ySize + uvSize)

    // Luminancia: se copia fila por fila porque el row stride puede traer relleno.
    val yBuffer = yPlane.buffer
    var offset = 0
    if (yPlane.rowStride == width) {
        yBuffer.get(nv21, 0, ySize)
        offset = ySize
    } else {
        val row = ByteArray(yPlane.rowStride)
        for (line in 0 until height) {
            yBuffer.position(line * yPlane.rowStride)
            yBuffer.get(row, 0, minOf(row.size, yBuffer.remaining()))
            row.copyInto(nv21, offset, 0, width)
            offset += width
        }
    }

    // NV21 intercala las crominancias como V,U a media resolución.
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    val chromaHeight = height / 2
    val chromaWidth = width / 2

    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            val uvIndex = row * uPlane.rowStride + col * uPlane.pixelStride
            if (uvIndex >= vBuffer.limit() || uvIndex >= uBuffer.limit()) continue
            nv21[offset++] = vBuffer.get(uvIndex)
            nv21[offset++] = uBuffer.get(uvIndex)
        }
    }

    return CameraFrame(
        nv21 = nv21,
        width = width,
        height = height,
        rotationDegrees = imageInfo.rotationDegrees,
    )
}
