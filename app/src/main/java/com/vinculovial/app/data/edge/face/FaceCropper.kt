package com.vinculovial.app.data.edge.face

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encuentra la cara dominante de una imagen y la recorta.
 *
 * Se usa en las dos puntas de la comparación: la foto impresa del frente del DNI y la
 * selfie. Recortar antes de embeber importa — un embedder alimentado con la foto entera
 * codifica también el fondo y el documento, y la similitud deja de significar nada.
 */
interface FaceLocator {

    /** `null` si no hay una cara utilizable. El bitmap devuelto es responsabilidad del llamador. */
    suspend fun cropDominantFace(image: Bitmap, source: FaceSource): Bitmap?
}

/**
 * De dónde sale la cara que se busca. No es una etiqueta descriptiva: cambia con qué
 * detector se busca.
 *
 * Las dos puntas de la comparación tienen escalas radicalmente distintas —la foto impresa
 * del DNI mide ~19mm de ancho sobre una tarjeta de 85.6mm, mientras que en una selfie la
 * cara ocupa medio cuadro— y un único umbral de tamaño mínimo tiene que elegir a cuál
 * servir. Antes había uno solo y lo pagaba el documento: cualquier valor lo bastante
 * permisivo para la foto impresa volvía la detección de la selfie innecesariamente cara y
 * propensa a enganchar caras de fondo.
 */
enum class FaceSource {
    /** Foto impresa del frente del DNI: chiquita, de baja resolución y con reflejos. */
    DOCUMENT,

    /** Selfie tomada de cerca: la cara domina el cuadro. */
    SELFIE,
}

@Singleton
class MlKitFaceLocator @Inject constructor() : FaceLocator {

    /**
     * Un detector por escala. Los dos en modo preciso: acá no hay presión de tiempo real
     * (son dos imágenes fijas) y conviene la detección más fiable posible.
     */
    private val documentDetector by lazy { detectorWithMinFaceSize(DOCUMENT_MIN_FACE_SIZE) }
    private val selfieDetector by lazy { detectorWithMinFaceSize(SELFIE_MIN_FACE_SIZE) }

    private fun detectorWithMinFaceSize(minFaceSize: Float) = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(minFaceSize)
            .build(),
    )

    override suspend fun cropDominantFace(image: Bitmap, source: FaceSource): Bitmap? {
        val detector = when (source) {
            FaceSource.DOCUMENT -> documentDetector
            FaceSource.SELFIE -> selfieDetector
        }
        val faces = detector.process(InputImage.fromBitmap(image, 0)).await()

        // La cara dominante es la más grande: en la foto de un DNI puede aparecer también
        // la miniatura fantasma, y en una selfie alguien de fondo.
        val face = faces.maxByOrNull { it.boundingBox.width().toLong() * it.boundingBox.height() }
            ?: return null

        return image.cropTo(face.squareBoxWithin(image.width, image.height))
    }

    /**
     * Recorte **cuadrado** y con margen alrededor de la cara.
     *
     * Dos cosas, y las dos afectan directamente la similitud:
     *
     * 1. **Margen**: el bounding box de ML Kit corta al ras; los modelos de reconocimiento se
     *    entrenan con algo de frente, mentón y orejas. Sin ese margen la similitud cae de
     *    forma sistemática.
     * 2. **Cuadrado**: el modelo recibe 112x112, y el recorte se estira hasta ese tamaño. Un
     *    recorte con otra relación de aspecto se deforma al estirarse — y ahí está el
     *    problema fino: la cara impresa del DNI y la de una selfie **no** tienen la misma
     *    relación de aspecto, así que cada una se deformaba distinto y el modelo terminaba
     *    comparando dos geometrías que ya no se correspondían. Recortar cuadrado desde el
     *    principio hace que las dos puntas de la comparación reciban exactamente el mismo
     *    trato.
     *
     * El cuadrado se centra en la cara y se achica si no entra en la imagen, de modo que el
     * resultado siempre es cuadrado y siempre cae dentro de los límites (el bounding box de
     * ML Kit puede salirse del cuadro cuando la cara está cortada por el borde).
     */
    private fun Face.squareBoxWithin(imageWidth: Int, imageHeight: Int): Rect {
        val box = boundingBox
        val withMargin = maxOf(
            box.width() * (1f + 2 * FACE_MARGIN),
            box.height() * (1f + 2 * FACE_MARGIN),
        )

        // Si la cara es más grande que la imagen (cara cortada por el borde), el lado se
        // limita a lo que hay; nunca se piden píxeles que no existen.
        val side = withMargin.toInt().coerceAtMost(minOf(imageWidth, imageHeight))

        // Se centra en la cara y después se corre lo mínimo indispensable para que entre.
        val left = (box.centerX() - side / 2).coerceIn(0, imageWidth - side)
        val top = (box.centerY() - side / 2).coerceIn(0, imageHeight - side)

        return Rect(left, top, left + side, top + side)
    }

    private fun Bitmap.cropTo(rect: Rect): Bitmap? {
        if (rect.width() <= 0 || rect.height() <= 0) return null
        return Bitmap.createBitmap(this, rect.left, rect.top, rect.width(), rect.height())
    }

    private companion object {
        /**
         * Proporción mínima del ancho de la imagen que debe ocupar la cara del **documento**.
         *
         * Es el único límite de tamaño que queda en todo el flujo del DNI, y está puesto para
         * **no** ser el que decide. La cuenta que lo fija: con el documento sostenido a ~25cm
         * —la distancia a la que el lente enfoca sin pelear— la tarjeta ocupa cerca del 27%
         * del ancho del cuadro y la cara impresa apenas un 4%. Cualquier valor por encima de
         * eso obliga a acercarse, que es exactamente el círculo vicioso que había que romper:
         * de 0.12 sólo pasaba un encuadre perfecto, y de 0.05 todavía quedaba afuera la mesa.
         *
         * El costo de bajarlo es detección más lenta y algún candidato espurio, pero la cara
         * dominante se elige por área, así que un falso positivo chico pierde contra la foto
         * real.
         */
        const val DOCUMENT_MIN_FACE_SIZE = 0.03f

        /**
         * En una selfie la cara ocupa buena parte del cuadro, así que un piso alto es gratis
         * y encima aporta: descarta de entrada a alguien parado de fondo, que es justo el
         * caso que no queremos que gane la selección por área.
         */
        const val SELFIE_MIN_FACE_SIZE = 0.15f

        /** Margen alrededor del bounding box, como fracción de su tamaño. */
        const val FACE_MARGIN = 0.20f
    }
}
