package com.vinculovial.app.data.edge

import java.nio.charset.StandardCharsets
import kotlin.random.Random

/**
 * Codificación de las "fotos" sintéticas que usa el mock de captura.
 *
 * En lugar de imágenes reales generamos un blob binario con una cabecera legible:
 * `VVMOCK1|<kind>|<payload>|` seguida de ruido. Eso permite que el OCR y el Liveness
 * mock sean deterministas y testeables sin CameraX ni ML Kit.
 */
internal object MockImageCodec {

    const val KIND_DNI_FRONT = "DNI_FRONT"
    const val KIND_SELFIE = "SELFIE"

    /** Firma que el mock de selfie deja en la imagen para simular un rostro detectado. */
    const val FACE_SIGNATURE = "FACE_OK"

    private const val MAGIC = "VVMOCK1"
    private const val SEPARATOR = '|'
    private const val NOISE_BYTES = 512

    fun encode(kind: String, payload: String, random: Random = Random.Default): ByteArray {
        val header = "$MAGIC$SEPARATOR$kind$SEPARATOR$payload$SEPARATOR".toByteArray(StandardCharsets.US_ASCII)
        val noise = ByteArray(NOISE_BYTES).also(random::nextBytes)
        return header + noise
    }

    /** `null` si el blob no es una imagen mock válida. */
    fun decode(image: ByteArray): MockImage? {
        if (image.isEmpty()) return null
        val headerEnd = image.indexOfThirdSeparator() ?: return null
        val parts = String(image, 0, headerEnd, StandardCharsets.US_ASCII).split(SEPARATOR)
        if (parts.size != 3 || parts[0] != MAGIC) return null
        return MockImage(kind = parts[1], payload = parts[2])
    }

    private fun ByteArray.indexOfThirdSeparator(): Int? {
        var seen = 0
        forEachIndexed { index, byte ->
            if (byte == SEPARATOR.code.toByte()) {
                seen++
                if (seen == 3) return index
            }
        }
        return null
    }
}

internal data class MockImage(val kind: String, val payload: String)
