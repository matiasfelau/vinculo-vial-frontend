package com.vinculovial.app.domain.signup.model

/**
 * En Argentina conviven dos formatos de DNI:
 * - el **actual**: PDF417 en el mismo frente, junto a la foto;
 * - el **DNI electrónico** (tarjeta con chip, en emisión desde 2026): sólo la foto en el
 *   frente, QR en el reverso.
 *
 * El escaneo por eso tiene dos modos: en el frente se busca cara y código a la vez (para
 * saber cuál de los dos formatos es); en el reverso, si hace falta, sólo el código.
 */
enum class DocumentScanMode {
    /** Frente del documento: busca cara y código de barras al mismo tiempo. */
    FACE_AND_BARCODE,

    /** Reverso del documento: sólo busca el código (QR del DNI electrónico). */
    BARCODE_ONLY,
}

/**
 * Qué se decidió a partir de lo observado en el frame. Mientras es [NONE] el escaneo
 * sigue en curso; cualquier otro valor le indica a la pantalla que capture la foto y
 * qué hacer después (terminar, o pasar a escanear el reverso).
 */
enum class DocumentScanOutcome {
    NONE,

    /** Frente con cara y código a la vez: DNI actual. No hace falta el reverso. */
    LEGACY_WITH_BARCODE,

    /** Frente con cara pero sin código: DNI electrónico. Hay que escanear el reverso. */
    ELECTRONIC_NO_BARCODE,

    /** Reverso con código legible. */
    BARCODE_FOUND,
}

data class DocumentScanProgress(
    val mode: DocumentScanMode,
    val faceDetected: Boolean = false,
    val barcodeDetected: Boolean = false,
    val hint: FramingHint = FramingHint.NONE,
    val outcome: DocumentScanOutcome = DocumentScanOutcome.NONE,
) {
    val isDecided: Boolean get() = outcome != DocumentScanOutcome.NONE
}
