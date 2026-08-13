package com.vinculovial.app.domain.signup.model

/**
 * Resultado del procesamiento Edge (en el dispositivo): el DNI leído por OCR del frente
 * del documento y el veredicto de Liveness entre esa foto y la selfie.
 *
 * Es el único dato que sobrevive a las imágenes: los bytes se descartan al producirlo.
 */
data class EdgeResult(
    val dni: String?,
    val isLive: Boolean,
) {
    /** Nunca exponer el DNI en logs. */
    override fun toString(): String = "EdgeResult(dni=${if (dni == null) "null" else "***"}, isLive=$isLive)"
}
