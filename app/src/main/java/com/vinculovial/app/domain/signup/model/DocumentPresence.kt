package com.vinculovial.app.domain.signup.model

/**
 * Qué se ve en el stream de la cámara mientras el usuario sostiene el documento.
 *
 * Es **geometría, no lectura**. La distinción es toda la arquitectura: el stream de análisis
 * no tiene resolución para decodificar un PDF417 (harían falta ~2.5 píxeles por módulo, y a
 * 1280px de ancho el código recibe menos de la mitad), pero sí alcanza de sobra para
 * responder "¿hay un rectángulo de código de barras y una cara acá adentro, y están quietos?".
 * Esa respuesta es todo lo que hace falta para decidir **cuándo** disparar la foto de alta
 * resolución, que es la que sí se decodifica.
 */
data class DocumentPresence(
    val stage: DocumentPresenceStage = DocumentPresenceStage.SEARCHING,
    /** Se ve la caja de un código de barras, se pueda decodificar o no. */
    val hasBarcode: Boolean = false,
    /** Se ve la foto impresa del titular. */
    val hasFace: Boolean = false,
) {
    /** Momento de disparar: las dos geometrías presentes y la escena quieta. */
    val isReadyToCapture: Boolean get() = stage == DocumentPresenceStage.STEADY
}

enum class DocumentPresenceStage {
    /** Todavía no se ve algo que parezca el frente de un DNI. */
    SEARCHING,

    /** Están las dos geometrías, pero la escena se sigue moviendo. */
    DETECTED,

    /** Estable durante varios frames seguidos: se puede disparar. */
    STEADY,
}
