package ar.felaufernandezbottaro.vinculovial.data.evidence

import ar.felaufernandezbottaro.vinculovial.data.edge.SecureImageBuffer
import ar.felaufernandezbottaro.vinculovial.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface EvidenceProcessingResult {

    /** El JPEG listo para cifrar, en un buffer que el llamador tiene que liberar. */
    data class Processed(val photo: SecureImageBuffer) : EvidenceProcessingResult

    /** Los bytes no son una imagen decodificable. */
    data object Unreadable : EvidenceProcessingResult

    /** Ni a la calidad mínima entra en [EvidenceImageProcessor.MAX_JPEG_BYTES]. */
    data object TooLarge : EvidenceProcessingResult
}

/**
 * Deja una foto de evidencia lista para cifrar: derecha, con el lado mayor a lo sumo
 * [MAX_SIDE_PX] y re-codificada como JPEG de a lo sumo [MAX_JPEG_BYTES], bajando la calidad de
 * [START_QUALITY] a [MIN_QUALITY] hasta que entre. El re-encode desde píxeles descarta el EXIF.
 *
 * Los límites son los del contrato: cada parte del multipart pesa a lo sumo 1 MiB, y con
 * ~900 KB por foto el body completo entra en los 5 MB de Kong.
 *
 * Toma posesión de los bytes de entrada y los sobreescribe con ceros al terminar; las imágenes
 * intermedias y los JPEG de los intentos descartados también se limpian.
 */
@Singleton
class EvidenceImageProcessor @Inject constructor(
    private val rasterFactory: EvidenceRasterFactory,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {

    suspend fun process(jpeg: ByteArray, rotationDegrees: Int): EvidenceProcessingResult =
        withContext(dispatcher) {
            try {
                processInPlace(jpeg, rotationDegrees)
            } finally {
                jpeg.fill(0)
            }
        }

    private fun processInPlace(jpeg: ByteArray, rotationDegrees: Int): EvidenceProcessingResult {
        val decoded = rasterFactory.decodeUpright(jpeg, rotationDegrees, MAX_SIDE_PX)
            ?: return EvidenceProcessingResult.Unreadable

        val fitted = try {
            decoded.scaledToFit(MAX_SIDE_PX)
        } catch (e: RuntimeException) {
            decoded.wipe()
            throw e
        }

        return try {
            encodeWithinBudget(fitted)
        } finally {
            fitted.wipe()
        }
    }

    private fun encodeWithinBudget(raster: EvidenceRaster): EvidenceProcessingResult {
        var quality = START_QUALITY
        while (quality >= MIN_QUALITY) {
            val encoded = raster.toJpeg(quality)
            if (encoded.size <= MAX_JPEG_BYTES) {
                return EvidenceProcessingResult.Processed(SecureImageBuffer.wrap(encoded))
            }
            encoded.fill(0)
            quality -= QUALITY_STEP
        }
        return EvidenceProcessingResult.TooLarge
    }

    companion object {
        const val MAX_SIDE_PX = 1_600
        const val MAX_JPEG_BYTES = 900 * 1024
        const val START_QUALITY = 85
        const val MIN_QUALITY = 60
        const val QUALITY_STEP = 5
    }
}
