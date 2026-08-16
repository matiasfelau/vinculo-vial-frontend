package ar.felaufernandezbottaro.vinculovial.data.edge

import android.graphics.Bitmap
import android.util.Base64
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Prepara la selfie para viajar dentro del payload cifrado: es la única imagen que sale del
 * dispositivo (la verificación biométrica ahora la hace el backend contra RENAPER), así que
 * el tamaño del payload importa de un modo en el que nunca importó mientras todo se
 * comparaba en el Edge.
 *
 * Reduce a un máximo de 800x800 (nunca agranda) y recomprime en JPEG, bajando la calidad en
 * escalones si hace falta hasta entrar en un presupuesto de bytes — así el string Base64
 * resultante se mantiene por debajo de ~200KB.
 */
@Singleton
class SelfieCompressor @Inject constructor() {

    /** `null` si el JPEG de origen no se pudo decodificar. */
    fun compress(jpeg: ByteArray): String? {
        // targetDimension acá es sólo el primer paso, grueso (inSampleSize, potencias de
        // dos): deja el lado mayor entre 800 y 1600. El ajuste fino a <=800 exacto lo hace
        // scaledToFit() después. Decodificar ya submuestreado evita materializar en memoria
        // el bitmap completo de una foto de varios megapíxeles sólo para tirar casi todo.
        val original = SecureBitmap.decode(jpeg, targetDimension = MAX_DIMENSION_PX) ?: return null

        val resized = original.scaledToFit(MAX_DIMENSION_PX)
        return try {
            resized.toBudgetedBase64()
        } finally {
            SecureBitmap.wipe(resized)
            if (resized !== original) SecureBitmap.wipe(original)
        }
    }

    /** Nunca agranda: sólo achica cuando el lado más largo supera el máximo. */
    private fun Bitmap.scaledToFit(maxDimensionPx: Int): Bitmap {
        val largestSide = maxOf(width, height)
        if (largestSide <= maxDimensionPx) return this

        val scaleFactor = maxDimensionPx.toFloat() / largestSide
        val targetWidth = (width * scaleFactor).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scaleFactor).roundToInt().coerceAtLeast(1)
        return scale(targetWidth, targetHeight)
    }

    /**
     * Comprime bajando la calidad en escalones hasta entrar en [MAX_RAW_JPEG_BYTES], o hasta
     * tocar el piso de calidad — lo que pase primero. El presupuesto es de bytes JPEG
     * crudos, no del string final: Base64 infla ~4/3, así que ~150KB de JPEG rinden
     * ~200KB de texto.
     */
    private fun Bitmap.toBudgetedBase64(): String {
        var quality = INITIAL_JPEG_QUALITY
        var jpegBytes: ByteArray

        do {
            jpegBytes = ByteArrayOutputStream().use { out ->
                compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= QUALITY_STEP
        } while (jpegBytes.size > MAX_RAW_JPEG_BYTES && quality >= MIN_JPEG_QUALITY)

        return Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
    }

    private companion object {
        const val MAX_DIMENSION_PX = 800
        const val INITIAL_JPEG_QUALITY = 80
        const val QUALITY_STEP = 10
        const val MIN_JPEG_QUALITY = 40

        /** ~200KB de Base64 ÷ (4/3) ≈ 150KB de JPEG crudo. */
        const val MAX_RAW_JPEG_BYTES = 150 * 1024
    }
}
