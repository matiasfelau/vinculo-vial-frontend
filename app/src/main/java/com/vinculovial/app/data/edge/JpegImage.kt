package com.vinculovial.app.data.edge

/** Dimensiones leídas de la cabecera JPEG, en píxeles. */
internal data class ImageDimensions(val width: Int, val height: Int) {
    val megapixels: Double get() = (width.toLong() * height) / 1_000_000.0
}

/**
 * Lectura mínima de JPEG, sin decodificar la imagen.
 *
 * Sirve para descartar capturas inservibles (buffer vacío, foto truncada, resolución
 * demasiado baja para leer un documento) antes de gastar el pipeline Edge en ellas.
 * Se parsea sólo la cabecera: nunca se carga el bitmap completo en memoria.
 */
internal object JpegImage {

    /** Marcadores SOF que declaran el tamaño; se excluyen DHT (C4), JPG (C8) y DAC (CC). */
    private val START_OF_FRAME = buildSet {
        addAll(0xC0..0xC3)
        addAll(0xC5..0xC7)
        addAll(0xC9..0xCB)
        addAll(0xCD..0xCF)
    }

    private const val MARKER = 0xFF
    private const val START_OF_IMAGE = 0xD8
    private const val START_OF_SCAN = 0xDA

    fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size > 3 &&
            bytes.byteAt(0) == MARKER &&
            bytes.byteAt(1) == START_OF_IMAGE

    /** `null` si no es un JPEG o si la cabecera está incompleta. */
    fun dimensionsOf(bytes: ByteArray): ImageDimensions? {
        if (!isJpeg(bytes)) return null

        var cursor = 2
        while (cursor + 1 < bytes.size) {
            // Puede haber bytes 0xFF de relleno entre segmentos.
            if (bytes.byteAt(cursor) != MARKER) {
                cursor++
                continue
            }

            val marker = bytes.byteAt(cursor + 1)
            if (marker == MARKER) {
                cursor++
                continue
            }

            // Marcadores sin payload: SOI, EOI, RSTn y TEM.
            if (marker == 0x01 || marker in 0xD0..0xD9) {
                cursor += 2
                continue
            }

            if (cursor + 3 >= bytes.size) return null
            val segmentLength = (bytes.byteAt(cursor + 2) shl 8) or bytes.byteAt(cursor + 3)

            if (marker in START_OF_FRAME) {
                // FF | marcador | largo(2) | precisión(1) | alto(2) | ancho(2)
                if (cursor + 8 >= bytes.size) return null
                return ImageDimensions(
                    height = (bytes.byteAt(cursor + 5) shl 8) or bytes.byteAt(cursor + 6),
                    width = (bytes.byteAt(cursor + 7) shl 8) or bytes.byteAt(cursor + 8),
                )
            }

            // Empezaron los datos comprimidos: ya no va a aparecer un SOF.
            if (marker == START_OF_SCAN || segmentLength < 2) return null

            cursor += 2 + segmentLength
        }
        return null
    }

    private fun ByteArray.byteAt(index: Int): Int = this[index].toInt() and 0xFF
}
