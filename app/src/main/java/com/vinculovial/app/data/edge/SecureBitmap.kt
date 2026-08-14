package com.vinculovial.app.data.edge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color

/**
 * Bitmaps de vida controlada, para que el Zero Retention no se rompa en el momento en
 * que la imagen deja de ser un JPEG comprimido.
 *
 * El pipeline biométrico necesita píxeles: ML Kit y TFLite no trabajan sobre bytes
 * comprimidos. Ese bitmap decodificado es una segunda copia de la cara del usuario, y
 * `Bitmap.recycle()` por sí solo no garantiza que el contenido deje de ser legible en el
 * heap nativo. Acá se sobreescribe con opaco antes de reciclar.
 */
internal object SecureBitmap {

    /**
     * Decodifica un JPEG en un bitmap mutable, submuestreado si hace falta.
     *
     * @param maxDimension lado mayor tolerado. Las fotos de cámara moderna son enormes y
     * ni el detector ni el embedder ganan nada con esa resolución; bajar el tamaño acota
     * cuánta memoria contiene la cara.
     */
    fun decode(jpeg: ByteArray, maxDimension: Int = 1_600): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
    }

    /** Sobreescribe el contenido y libera el bitmap. Tolera `null` y bitmaps ya reciclados. */
    fun wipe(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        runCatching {
            if (bitmap.isMutable) bitmap.eraseColor(Color.BLACK)
        }
        bitmap.recycle()
    }

    /** Ejecuta [block] y borra el bitmap después, incluso si falla. */
    inline fun <R> Bitmap.useSecurely(block: (Bitmap) -> R): R = try {
        block(this)
    } finally {
        wipe(this)
    }

    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > maxDimension) {
            sample *= 2
        }
        return sample
    }
}
