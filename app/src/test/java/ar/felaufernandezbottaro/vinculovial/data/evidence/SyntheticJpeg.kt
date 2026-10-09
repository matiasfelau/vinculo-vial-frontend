package ar.felaufernandezbottaro.vinculovial.data.evidence

import kotlin.math.roundToInt

/**
 * JPEG sintético para probar el pipeline de evidencia en JVM. Los tests unitarios de Android
 * compilan contra `android.jar`, donde no existen ni `Bitmap` real ni `javax.imageio`, así que
 * esto arma y parsea sólo la **estructura de segmentos** de un JPEG (SOI, APP1/EXIF, APP0, SOF0,
 * SOS, EOI): lo justo para comprobar dimensiones, peso y presencia de EXIF "parseando el JPEG".
 * La decodificación y compresión de píxeles reales es de `AndroidEvidenceRaster`, que depende
 * de `Bitmap` y no se prueba acá.
 */
data class JpegInfo(val width: Int, val height: Int, val hasExif: Boolean)

/** JPEG de [width]x[height] con [payloadBytes] de datos de imagen y, si se pide, un segmento APP1 "Exif". */
fun syntheticJpeg(width: Int, height: Int, payloadBytes: Int = 64, withExif: Boolean = false): ByteArray {
    val out = mutableListOf<Byte>()
    fun marker(code: Int) {
        out += 0xFF.toByte()
        out += code.toByte()
    }
    fun word(value: Int) {
        out += (value shr 8).toByte()
        out += value.toByte()
    }

    marker(SOI)
    if (withExif) {
        val exif = "Exif".toByteArray(Charsets.US_ASCII).toList() + listOf<Byte>(0, 0) + List(EXIF_FILLER_BYTES) { 1.toByte() }
        marker(APP1)
        word(exif.size + 2)
        out += exif
    }
    marker(APP0)
    word(JFIF_LENGTH)
    out += "JFIF".toByteArray(Charsets.US_ASCII).toList() + List(JFIF_LENGTH - 2 - 4) { 0.toByte() }
    marker(SOF0)
    word(SOF0_LENGTH)
    out += 8.toByte()
    word(height)
    word(width)
    out += List(SOF0_LENGTH - 2 - 1 - 2 - 2) { 1.toByte() }
    marker(SOS)
    word(SOS_LENGTH)
    out += List(SOS_LENGTH - 2) { 1.toByte() }
    out += List(payloadBytes) { 2.toByte() }
    marker(EOI)
    return out.toByteArray()
}

/** Recorre los segmentos hasta SOS. `null` si no empieza con SOI o no trae SOF0. */
fun jpegInfo(jpeg: ByteArray): JpegInfo? {
    if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != SOI.toByte()) return null

    var position = 2
    var size: Pair<Int, Int>? = null
    var hasExif = false
    while (position + 4 <= jpeg.size && jpeg[position] == 0xFF.toByte()) {
        val marker = jpeg[position + 1].toInt() and 0xFF
        if (marker == SOS) break

        val length = jpeg.word(position + 2)
        when (marker) {
            APP1 -> if (jpeg.startsWithAt(position + 4, "Exif")) hasExif = true
            SOF0 -> size = jpeg.word(position + 7) to jpeg.word(position + 5)
        }
        position += 2 + length
    }
    return size?.let { (width, height) -> JpegInfo(width, height, hasExif) }
}

fun hasExif(jpeg: ByteArray): Boolean = jpegInfo(jpeg)?.hasExif == true

/** Raster que sólo conoce sus dimensiones; [toJpeg] pesa lo que pesaría un JPEG de ese tamaño y calidad. */
class SyntheticRaster(override val width: Int, override val height: Int) : EvidenceRaster {

    var isWiped = false
        private set

    override fun scaledToFit(maxSide: Int): EvidenceRaster {
        val largestSide = maxOf(width, height)
        if (largestSide <= maxSide) return this

        val factor = maxSide.toDouble() / largestSide
        val scaled = SyntheticRaster(
            (width * factor).roundToInt().coerceAtLeast(1),
            (height * factor).roundToInt().coerceAtLeast(1),
        )
        wipe()
        return scaled
    }

    override fun toJpeg(quality: Int): ByteArray =
        syntheticJpeg(width, height, payloadBytes = width * height * quality / BYTES_DIVISOR)

    override fun wipe() {
        isWiped = true
    }

    private companion object {
        /** Calibrado para que 1600x1200 pese ~816 KB a calidad 85 y ~576 KB a calidad 60. */
        const val BYTES_DIVISOR = 200
    }
}

class SyntheticRasterFactory : EvidenceRasterFactory {

    val decoded = mutableListOf<SyntheticRaster>()
    val rotationsReceived = mutableListOf<Int>()

    override fun decodeUpright(jpeg: ByteArray, rotationDegrees: Int, targetSide: Int): EvidenceRaster? {
        rotationsReceived += rotationDegrees
        val info = jpegInfo(jpeg) ?: return null

        val quarterTurn = rotationDegrees % HALF_TURN_DEGREES != 0
        val raster = if (quarterTurn) SyntheticRaster(info.height, info.width) else SyntheticRaster(info.width, info.height)
        return raster.also { decoded += it }
    }

    private companion object {
        const val HALF_TURN_DEGREES = 180
    }
}

private fun ByteArray.word(offset: Int): Int = ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

private fun ByteArray.startsWithAt(offset: Int, text: String): Boolean =
    text.indices.all { index -> getOrNull(offset + index) == text[index].code.toByte() }

private const val SOI = 0xD8
private const val EOI = 0xD9
private const val APP0 = 0xE0
private const val APP1 = 0xE1
private const val SOF0 = 0xC0
private const val SOS = 0xDA
private const val JFIF_LENGTH = 16
private const val SOF0_LENGTH = 17
private const val SOS_LENGTH = 12
private const val EXIF_FILLER_BYTES = 8
