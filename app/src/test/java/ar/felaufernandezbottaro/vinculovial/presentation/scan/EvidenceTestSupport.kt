package ar.felaufernandezbottaro.vinculovial.presentation.scan

import androidx.compose.runtime.Composable
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceRaster
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceRasterFactory
import ar.felaufernandezbottaro.vinculovial.presentation.evidence.GalleryEvidenceSource

/**
 * Fábrica falsa: cada foto "procesada" es un arreglo distinto lleno de unos, y se guarda en
 * [encoded] para poder comprobar después que el `ViewModel` lo sobreescribió con ceros.
 */
class StubEvidenceRasterFactory : EvidenceRasterFactory {

    val encoded = mutableListOf<ByteArray>()
    val rotationsReceived = mutableListOf<Int>()

    /** Programar `false` hace que la imagen sea ilegible. */
    var decodable = true

    /** Tamaño del JPEG "procesado"; por encima de 900 KB el procesador falla con `TooLarge`. */
    var encodedSize = 100

    override fun decodeUpright(jpeg: ByteArray, rotationDegrees: Int, targetSide: Int): EvidenceRaster? {
        rotationsReceived += rotationDegrees
        if (!decodable) return null

        return object : EvidenceRaster {
            override val width = 100
            override val height = 100
            override fun scaledToFit(maxSide: Int): EvidenceRaster = this
            override fun toJpeg(quality: Int): ByteArray = ByteArray(encodedSize) { 1 }.also { encoded += it }
            override fun wipe() = Unit
        }
    }

    fun allWiped(): Boolean = encoded.isNotEmpty() && encoded.all { array -> array.all { it == 0.toByte() } }

    fun anyAlive(): Boolean = encoded.any { array -> array.any { it != 0.toByte() } }
}

class FakeGalleryEvidenceSource(override val isAvailable: Boolean = false) : GalleryEvidenceSource {

    @Composable
    override fun rememberPickerLauncher(onPicked: (ByteArray) -> Unit): (() -> Unit)? = null
}
