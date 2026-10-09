package ar.felaufernandezbottaro.vinculovial.presentation.evidence

import androidx.compose.runtime.Composable

/**
 * Fuente de fotos de evidencia desde la galería: **sólo para desarrollo**.
 *
 * En producción las fotos se capturan únicamente con la cámara in-app. Esta interfaz vive en
 * `main`, pero su implementación real (el Photo Picker del sistema) está en `src/debug` y en
 * `src/release` hay una no-op, así que el código del picker no se compila en release: un flag
 * de UI no alcanzaría. La imagen elegida entra al mismo pipeline que una foto de cámara
 * (reescalado, re-encode sin EXIF, cifrado y wipe).
 */
interface GalleryEvidenceSource {

    /** `false` en release: el paso de captura no muestra la opción de la galería. */
    val isAvailable: Boolean

    /**
     * Devuelve la acción que abre el selector, o `null` si [isAvailable] es `false`. [onPicked]
     * recibe los bytes de la imagen elegida; quien los recibe los sobreescribe al terminar.
     */
    @Composable
    fun rememberPickerLauncher(onPicked: (ByteArray) -> Unit): (() -> Unit)?
}
