package com.vinculovial.app.data.edge

import javax.inject.Inject

/** Lee el número de documento del frente del DNI. */
interface OcrEngine {

    /**
     * @param dniFrontImage bytes JPEG del frente del documento.
     * @param declaredDni número que tipeó el usuario. **El motor real lo ignora**: lee el
     * documento. Existe en la firma sólo para [SimulatedOcrEngine], que todavía no
     * reconoce texto.
     * @return el número leído, o `null` si el documento no es legible.
     */
    fun extractDni(dniFrontImage: ByteArray, declaredDni: String): String?
}

/**
 * OCR simulado: valida que la foto sea utilizable de verdad (JPEG íntegro, resolución
 * suficiente) y, si lo es, da por leído el número declarado.
 *
 * Lo que **sí** es real acá es el rechazo: una captura vacía, truncada o de baja
 * resolución devuelve `null` y el registro se corta con "no pudimos leer tu DNI".
 *
 * Lo que **no** es real es la extracción: mientras no haya reconocimiento de texto
 * on-device, `RegisterUserUseCase.DniMismatch` no puede dispararse con este motor.
 * Sustituir por ML Kit (texto, o mejor el PDF417 del reverso del DNI argentino) sin
 * tocar nada más: el resto de la app depende de esta interfaz, no de la implementación.
 */
class SimulatedOcrEngine @Inject constructor() : OcrEngine {

    override fun extractDni(dniFrontImage: ByteArray, declaredDni: String): String? {
        if (!CaptureQuality.isUsableDocument(dniFrontImage)) return null
        return declaredDni.takeIf { DNI_PATTERN.matches(it) }
    }

    private companion object {
        val DNI_PATTERN = Regex("^\\d{7,8}$")
    }
}
