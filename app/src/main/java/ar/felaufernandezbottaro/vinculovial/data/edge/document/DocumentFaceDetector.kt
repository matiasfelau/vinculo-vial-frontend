package ar.felaufernandezbottaro.vinculovial.data.edge.document

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sólo presencia: ¿se ve una cara en la foto impresa del DNI?
 *
 * Con la biometría movida al backend (RENAPER), ya no hace falta recortar la cara con
 * margen ni una geometría cuadrada específica — eso existía únicamente para alimentar al
 * embedder local, que no existe más. Lo único que se necesita acá es una señal de calidad
 * de captura: si no se ve ninguna cara, probablemente la foto esté mal encuadrada, tapada
 * o el documento no sea el correcto.
 */
interface DocumentFaceDetector {

    suspend fun hasFace(image: Bitmap): Boolean
}

@Singleton
class MlKitDocumentFaceDetector @Inject constructor() : DocumentFaceDetector {

    /**
     * Modo rápido: esto corre en cada intento de captura (`canReadDniFront`), no hay
     * necesidad de la precisión de `PERFORMANCE_MODE_ACCURATE` para una pregunta de sí/no.
     */
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(MIN_FACE_SIZE)
                .build(),
        )
    }

    override suspend fun hasFace(image: Bitmap): Boolean =
        detector.process(InputImage.fromBitmap(image, 0)).await().isNotEmpty()

    private companion object {
        /**
         * La foto impresa del DNI es diminuta dentro del cuadro cuando el documento se
         * sostiene a una distancia donde el lente enfoca sin pelear (ver CLAUDE.md, sección
         * de resolución del escaneo).
         */
        const val MIN_FACE_SIZE = 0.03f
    }
}
