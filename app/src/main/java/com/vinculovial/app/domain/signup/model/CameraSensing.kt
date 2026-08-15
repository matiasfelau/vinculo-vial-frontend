package com.vinculovial.app.domain.signup.model

/**
 * Un frame de la cámara en formato neutral, para que `domain` no dependa de CameraX.
 *
 * NV21 es el formato que aceptan tanto CameraX (tras conversión desde YUV_420_888) como
 * ML Kit, así que evita una decodificación intermedia. Lo comparten la prueba de vida y
 * el escaneo de documento: ambos analizan el stream de la cámara en vivo, no una foto.
 */
class CameraFrame(
    val nv21: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
)

/** Corrección de encuadre que se le puede mostrar al usuario mientras dura un análisis en vivo. */
enum class FramingHint {
    NONE,
    NO_FACE,
    MULTIPLE_FACES,
    TOO_FAR,
    TOO_CLOSE,
    LOOK_STRAIGHT,
}
