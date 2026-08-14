package com.vinculovial.app.presentation.camera

/** Guía que se dibuja sobre el preview para encuadrar la toma. */
enum class CaptureGuide {
    /** Rectángulo con proporción de tarjeta, para el frente del DNI. */
    DOCUMENT,

    /** Óvalo centrado, para el rostro. */
    FACE,
}

/** Qué se está capturando y cómo se le explica al usuario. */
data class CameraCaptureSpec(
    val title: String,
    val instruction: String,
    val useFrontLens: Boolean,
    val guide: CaptureGuide,
)
