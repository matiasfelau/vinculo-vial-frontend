package com.vinculovial.app.domain.signup.model

/**
 * Qué se decidió a partir de lo observado en el frente del documento. Mientras es
 * [NONE] el escaneo sigue en curso.
 *
 * Por ahora la app sólo acepta el DNI actual (código en el mismo frente): el DNI
 * electrónico (código en el reverso) queda fuera de alcance del MVP — ver
 * `ElectronicDniQrParser`. Por eso no hay un tercer resultado "es electrónico, pedí el
 * reverso": si no aparece un código en el frente, [NOT_FOUND_TIMEOUT] se lo dice al
 * usuario y listo.
 */
enum class DocumentScanOutcome {
    NONE,

    /** Cara y código a la vez: se puede capturar. */
    FOUND,

    /** Hay cara hace rato pero nunca apareció un código: no es un DNI con código de barras. */
    NOT_FOUND_TIMEOUT,
}

data class DocumentScanProgress(
    val faceDetected: Boolean = false,
    val barcodeDetected: Boolean = false,
    val hint: FramingHint = FramingHint.NONE,
    val outcome: DocumentScanOutcome = DocumentScanOutcome.NONE,
) {
    val isDecided: Boolean get() = outcome != DocumentScanOutcome.NONE
}
