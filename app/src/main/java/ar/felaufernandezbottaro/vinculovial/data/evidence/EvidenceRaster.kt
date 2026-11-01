package ar.felaufernandezbottaro.vinculovial.data.evidence

/**
 * Imagen decodificada (píxeles) en el punto del pipeline de evidencia donde se reescala y se
 * vuelve a comprimir. Es una interfaz para que el pipeline se pueda probar en JVM, donde no
 * existe `Bitmap`: la implementación real es [AndroidEvidenceRaster].
 */
interface EvidenceRaster {
    val width: Int
    val height: Int

    /** Devuelve una imagen cuyo lado mayor es a lo sumo [maxSide]; nunca agranda. */
    fun scaledToFit(maxSide: Int): EvidenceRaster

    /** JPEG nuevo, sin metadatos: nada del archivo original (EXIF incluido) sobrevive. */
    fun toJpeg(quality: Int): ByteArray

    /** Sobreescribe los píxeles y libera la imagen. Idempotente. */
    fun wipe()
}

interface EvidenceRasterFactory {

    /**
     * Decodifica [jpeg] y lo deja derecho aplicando [rotationDegrees]. El lado mayor del
     * resultado puede superar [targetSide] (el submuestreo es por potencias de dos): el ajuste
     * fino lo hace [EvidenceRaster.scaledToFit]. `null` si no es una imagen decodificable.
     */
    fun decodeUpright(jpeg: ByteArray, rotationDegrees: Int, targetSide: Int): EvidenceRaster?
}
