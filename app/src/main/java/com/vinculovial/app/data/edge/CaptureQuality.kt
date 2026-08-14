package com.vinculovial.app.data.edge

/**
 * Puerta de calidad previa al análisis: una foto inservible se rechaza acá, con
 * criterios reales sobre los bytes capturados.
 *
 * No sustituye al análisis (eso es OCR/Liveness), pero evita que una captura vacía,
 * truncada o de resolución insuficiente llegue al pipeline y falle más adelante sin
 * poder explicarle nada útil al usuario.
 */
internal object CaptureQuality {

    /**
     * Un JPEG por debajo de esto es casi siempre un frame en negro o una captura
     * abortada: la compresión no baja tanto con una escena real.
     */
    private const val MIN_BYTES = 12_000

    /** Debajo de ~0.9 MP no hay resolución para leer el número de un documento. */
    private const val MIN_MEGAPIXELS = 0.9

    /** Suficiente para reconocer un rostro sin exigir la resolución de un documento. */
    private const val MIN_SELFIE_MEGAPIXELS = 0.3

    fun isUsableDocument(image: ByteArray): Boolean =
        isUsable(image, minMegapixels = MIN_MEGAPIXELS)

    fun isUsableSelfie(image: ByteArray): Boolean =
        isUsable(image, minMegapixels = MIN_SELFIE_MEGAPIXELS)

    private fun isUsable(image: ByteArray, minMegapixels: Double): Boolean {
        if (image.size < MIN_BYTES) return false
        val dimensions = JpegImage.dimensionsOf(image) ?: return false
        return dimensions.megapixels >= minMegapixels
    }
}
