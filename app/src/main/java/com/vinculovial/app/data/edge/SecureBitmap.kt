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
     * @param targetDimension piso de resolución para el lado mayor. **No es un tope**: como
     * `inSampleSize` sólo acepta potencias de dos, el resultado queda entre este valor y su
     * doble. La asimetría es deliberada — ver [sampleSizeFor].
     */
    fun decode(jpeg: ByteArray, targetDimension: Int = 1_600): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetDimension)
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

    /**
     * Potencia de dos que deja el lado mayor **por encima** de [targetDimension], nunca por
     * debajo.
     *
     * La versión anterior seguía dividiendo hasta quedar bajo el umbral, y como
     * `inSampleSize` avanza por potencias de dos eso significaba pasarse hasta a la mitad de
     * lo pedido: una captura de 4000px con un objetivo de 1600 terminaba en 1000px, un 37%
     * por debajo. Invisible para una cara —el embedder reduce a 112x112 igual—, pero letal
     * para el PDF417, que necesita un mínimo de píxeles por módulo y no tiene forma de
     * recuperarlos. Cortar antes de cruzar el umbral cuesta como mucho el doble de memoria y
     * es lo que mantiene el código de barras decodificable.
     */
    internal fun sampleSizeFor(width: Int, height: Int, targetDimension: Int): Int {
        val largestSide = maxOf(width, height)
        var sample = 1
        while (largestSide / (sample * 2) >= targetDimension) {
            sample *= 2
        }
        return sample
    }
}
