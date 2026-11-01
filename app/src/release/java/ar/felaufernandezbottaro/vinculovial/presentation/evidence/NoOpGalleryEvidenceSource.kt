package ar.felaufernandezbottaro.vinculovial.presentation.evidence

import androidx.compose.runtime.Composable

/** Release: no existe ninguna forma de adjuntar desde la galería. */
object NoOpGalleryEvidenceSource : GalleryEvidenceSource {

    override val isAvailable: Boolean = false

    @Composable
    override fun rememberPickerLauncher(onPicked: (ByteArray) -> Unit): (() -> Unit)? = null
}
