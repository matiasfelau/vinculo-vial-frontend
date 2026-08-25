package ar.felaufernandezbottaro.vinculovial.domain.scan.model

/**
 * Rectángulo del overlay de escaneo de patente, expresado como fracción (0..1) del frame ya
 * en posición vertical (post-rotación).
 *
 * Es la única fuente de verdad de esa región: la dibuja `ScanPlateScreen` sobre el preview y
 * la recorta `FrameCropper` sobre el frame de análisis. Compartir el mismo valor evita que la
 * zona que el usuario ve encuadrada se desalinee de la zona que efectivamente se analiza.
 */
data class OverlayRect(
    val leftFraction: Float,
    val topFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
) {
    companion object {
        /** Centrado, con una proporción cercana a la de una patente argentina (~340x130mm). */
        val PLATE = OverlayRect(
            leftFraction = 0.10f,
            topFraction = 0.39f,
            widthFraction = 0.80f,
            heightFraction = 0.22f,
        )
    }
}
