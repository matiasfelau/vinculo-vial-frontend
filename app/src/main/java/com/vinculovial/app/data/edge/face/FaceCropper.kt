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
    suspend fun cropDominantFace(image: Bitmap): Bitmap?
}

@Singleton
class MlKitFaceLocator @Inject constructor() : FaceLocator {

    /**
     * Modo preciso: acá no hay presión de tiempo real (son dos imágenes fijas) y
     * conviene la detección más fiable posible.
     */
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(MIN_FACE_SIZE)
                .build(),
        )
    }

    override suspend fun cropDominantFace(image: Bitmap): Bitmap? {
        val faces = detector.process(InputImage.fromBitmap(image, 0)).await()

        // La cara dominante es la más grande: en la foto de un DNI puede aparecer también
        // la miniatura fantasma, y en una selfie alguien de fondo.
        val face = faces.maxByOrNull { it.boundingBox.width().toLong() * it.boundingBox.height() }
            ?: return null

        return image.cropTo(face.expandedBox(image.width, image.height))
    }

    /**
     * El bounding box de ML Kit corta al ras de la cara; los modelos de reconocimiento
     * se entrenan con algo de margen (frente, mentón y orejas). Sin ese margen la
     * similitud cae de forma sistemática.
     */
    private fun Face.expandedBox(imageWidth: Int, imageHeight: Int): Rect {
        val box = boundingBox
        val marginX = (box.width() * FACE_MARGIN).toInt()
        val marginY = (box.height() * FACE_MARGIN).toInt()
        return Rect(
            (box.left - marginX).coerceAtLeast(0),
            (box.top - marginY).coerceAtLeast(0),
            (box.right + marginX).coerceAtMost(imageWidth),
            (box.bottom + marginY).coerceAtMost(imageHeight),
        )
    }

    private fun Bitmap.cropTo(rect: Rect): Bitmap? {
        if (rect.width() <= 0 || rect.height() <= 0) return null
        return Bitmap.createBitmap(this, rect.left, rect.top, rect.width(), rect.height())
    }

    private companion object {
        /** Proporción mínima del ancho de la imagen que debe ocupar la cara. */
        const val MIN_FACE_SIZE = 0.12f

        /** Margen alrededor del bounding box, como fracción de su tamaño. */
        const val FACE_MARGIN = 0.20f
    }
}
