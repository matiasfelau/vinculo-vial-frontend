package com.vinculovial.app.data.edge

import javax.inject.Inject

/** Lee el número de documento del frente del DNI. */
interface OcrEngine {
    /** `null` si la imagen no es legible o no contiene un DNI válido. */
    fun extractDni(image: ByteArray): String?
}

/**
 * Mock de OCR: interpreta las imágenes sintéticas de [MockCamera].
 * En producción, acá va ML Kit / Tesseract corriendo 100% on-device.
 */
class MockOcrEngine @Inject constructor() : OcrEngine {

    override fun extractDni(image: ByteArray): String? {
        val decoded = MockImageCodec.decode(image) ?: return null
        if (decoded.kind != MockImageCodec.KIND_DNI_FRONT) return null
        return decoded.payload.takeIf { DNI_PATTERN.matches(it) }
    }

    private companion object {
        val DNI_PATTERN = Regex("^\\d{7,8}$")
    }
}
