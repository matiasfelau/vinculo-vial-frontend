package ar.felaufernandezbottaro.vinculovial.data.evidence

import android.graphics.Bitmap
import android.graphics.Matrix
import ar.felaufernandezbottaro.vinculovial.data.edge.SecureBitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * [EvidenceRaster] sobre un `Bitmap`. `Bitmap.compress` escribe un JPEG nuevo sin EXIF ni
 * ningún otro metadato del archivo de origen: ese re-encode es lo que lo elimina.
 */
internal class AndroidEvidenceRaster(private val bitmap: Bitmap) : EvidenceRaster {

    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun scaledToFit(maxSide: Int): EvidenceRaster {
        val largestSide = maxOf(width, height)
        if (largestSide <= maxSide) return this

        val factor = maxSide.toFloat() / largestSide
        val scaled = bitmap.scale(
            (width * factor).roundToInt().coerceAtLeast(1),
            (height * factor).roundToInt().coerceAtLeast(1),
        )
        wipe()
        return AndroidEvidenceRaster(scaled)
    }

    override fun toJpeg(quality: Int): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }

    override fun wipe() = SecureBitmap.wipe(bitmap)
}

@Singleton
class AndroidEvidenceRasterFactory @Inject constructor() : EvidenceRasterFactory {

    override fun decodeUpright(jpeg: ByteArray, rotationDegrees: Int, targetSide: Int): EvidenceRaster? {
        val decoded = SecureBitmap.decode(jpeg, targetDimension = targetSide) ?: return null
        if (rotationDegrees % FULL_TURN_DEGREES == 0) return AndroidEvidenceRaster(decoded)

        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) SecureBitmap.wipe(decoded)
        return AndroidEvidenceRaster(upright)
    }

    private companion object {
        const val FULL_TURN_DEGREES = 360
    }
}
