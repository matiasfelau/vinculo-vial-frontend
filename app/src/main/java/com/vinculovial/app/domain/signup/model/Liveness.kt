package com.vinculovial.app.domain.signup.model

/**
 * Gesto que se le pide al usuario para probar que hay una persona presente.
 *
 * Se elige al azar en cada intento: si el desafío fuera siempre el mismo, alcanzaría
 * con un video grabado una sola vez.
 */
enum class LivenessChallenge {
    /** Pasar de cara neutra a sonrisa. */
    SMILE,

    /** Parpadear: ojos abiertos, cerrados y abiertos otra vez. */
    BLINK,
}

/** En qué punto del desafío está el usuario. */
enum class LivenessStage {
    /** Todavía no se ve una cara utilizable. */
    SEARCHING_FACE,

    /** Hay cara y se registró el estado de partida; falta el gesto. */
    WAITING_GESTURE,

    /** El gesto se completó: se puede tomar la foto. */
    PASSED,
}

/**
 * Estado del desafío de vida, tal como lo consume la UI.
 *
 * @param faceCoverage qué porción del encuadre ocupa la cara, para guiar al usuario a
 * acercarse o alejarse. `0` si no hay cara.
 */
data class LivenessProgress(
    val challenge: LivenessChallenge,
    val stage: LivenessStage = LivenessStage.SEARCHING_FACE,
    val faceCoverage: Float = 0f,
    val hint: LivenessHint = LivenessHint.NONE,
) {
    val isPassed: Boolean get() = stage == LivenessStage.PASSED
}

/** Corrección concreta que se le muestra al usuario mientras dura el desafío. */
enum class LivenessHint {
    NONE,
    NO_FACE,
    MULTIPLE_FACES,
    TOO_FAR,
    TOO_CLOSE,
    LOOK_STRAIGHT,
}

/**
 * Un frame de la cámara en formato neutral, para que `domain` no dependa de CameraX.
 *
 * NV21 es el formato que aceptan tanto CameraX (tras conversión desde YUV_420_888) como
 * ML Kit, así que evita una decodificación intermedia.
 */
class LivenessFrame(
    val nv21: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
)
