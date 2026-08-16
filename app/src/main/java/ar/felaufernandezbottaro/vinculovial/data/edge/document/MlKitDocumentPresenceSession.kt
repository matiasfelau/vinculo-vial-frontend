package ar.felaufernandezbottaro.vinculovial.data.edge.document

import android.graphics.Rect
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import ar.felaufernandezbottaro.vinculovial.domain.signup.DocumentPresenceSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CameraFrame
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentPresence
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentPresenceStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Presencia del documento con ML Kit sobre el stream de análisis.
 *
 * Corre dos detectores por frame, los dos en modo rápido y ninguno intentando entender el
 * contenido:
 * - **Código de barras** con [BarcodeScannerOptions.Builder.enableAllPotentialBarcodes], que
 *   es la pieza que hace posible todo esto: devuelve la caja de un código **aunque no lo
 *   pueda decodificar**. Sin esa opción el escáner es todo-o-nada y a la resolución del
 *   stream no decodificaría nunca, así que no habría señal.
 * - **Cara**, para distinguir el frente de un DNI de cualquier otro rectángulo con un código.
 *
 * Con las dos geometrías presentes se exige además que la escena esté **quieta** durante
 * varios frames seguidos. Ese requisito no es cosmético: dispara la foto en el momento en que
 * el usuario dejó de mover la mano, que es exactamente cuando el autoenfoque converge.
 */
@Singleton
class MlKitDocumentPresenceSession @Inject constructor() : DocumentPresenceSession {

    private val _presence = MutableStateFlow(DocumentPresence())
    override val presence: StateFlow<DocumentPresence> = _presence.asStateFlow()

    /** Un frame por vez: ML Kit no admite llamadas concurrentes sobre el mismo cliente. */
    private val inFlight = Mutex()
    private var lastAnalyzedAt = 0L
    private var running = false

    private var steadyFrames = 0
    private var lastBarcodeBox: Rect? = null

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_PDF417, Barcode.FORMAT_QR_CODE)
                // La clave del diseño: reporta códigos detectados pero no decodificados.
                .enableAllPotentialBarcodes()
                .build(),
        )
    }

    private val faceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                // Tiempo real: hay que seguirle el ritmo al preview, y acá sólo interesa la
                // presencia de una cara, no medirla con precisión.
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                // La foto impresa del DNI es diminuta dentro del cuadro cuando el documento
                // se sostiene a una distancia donde el lente enfoca.
                .setMinFaceSize(MIN_FACE_SIZE)
                .build(),
        )
    }

    override fun start() {
        steadyFrames = 0
        lastBarcodeBox = null
        lastAnalyzedAt = 0L
        running = true
        _presence.value = DocumentPresence()
    }

    override fun pause() {
        running = false
        steadyFrames = 0
        lastBarcodeBox = null
    }

    override suspend fun submitFrame(frame: CameraFrame) {
        if (!running) return

        val now = System.currentTimeMillis()
        if (now - lastAnalyzedAt < MIN_INTERVAL_MS) return
        // Si ya hay un frame en análisis se descarta este: interesa el más reciente.
        if (!inFlight.tryLock()) return

        try {
            lastAnalyzedAt = now
            val image = InputImage.fromByteArray(
                frame.nv21,
                frame.width,
                frame.height,
                frame.rotationDegrees,
                InputImage.IMAGE_FORMAT_NV21,
            )

            val barcodeBox = barcodeScanner.process(image).await()
                .maxByOrNull { it.boundingBox?.area() ?: 0 }
                ?.boundingBox
            val faceFound = faceDetector.process(image).await().isNotEmpty()

            // Si la sesión se pausó mientras se analizaba (la foto ya salió), este resultado
            // llega tarde y publicarlo volvería a marcar STEADY sobre una captura en curso.
            if (running) {
                _presence.value = evaluate(barcodeBox, faceFound)
            }
        } catch (e: Exception) {
            // Un frame que falla no invalida nada: se ignora y sigue el siguiente.
        } finally {
            inFlight.unlock()
        }
    }

    override fun close() {
        running = false
        barcodeScanner.close()
        faceDetector.close()
    }

    private fun evaluate(barcodeBox: Rect?, faceFound: Boolean): DocumentPresence {
        if (barcodeBox == null || !faceFound) {
            steadyFrames = 0
            lastBarcodeBox = null
            return DocumentPresence(
                stage = DocumentPresenceStage.SEARCHING,
                hasBarcode = barcodeBox != null,
                hasFace = faceFound,
            )
        }

        steadyFrames = if (lastBarcodeBox?.movedLittleTo(barcodeBox) == true) steadyFrames + 1 else 1
        lastBarcodeBox = barcodeBox

        return DocumentPresence(
            stage = if (steadyFrames >= REQUIRED_STEADY_FRAMES) {
                DocumentPresenceStage.STEADY
            } else {
                DocumentPresenceStage.DETECTED
            },
            hasBarcode = true,
            hasFace = true,
        )
    }

    /**
     * Quietud medida en proporción al tamaño del propio código, no en píxeles absolutos:
     * así el criterio no cambia según a qué distancia se sostenga el documento.
     */
    private fun Rect.movedLittleTo(other: Rect): Boolean {
        val tolerance = maxOf(width(), other.width()) * MOVEMENT_TOLERANCE
        return abs(centerX() - other.centerX()) <= tolerance &&
            abs(centerY() - other.centerY()) <= tolerance
    }

    private fun Rect.area(): Int = width() * height()

    private companion object {
        /** ~8 análisis por segundo; más rápido no aporta y calienta el dispositivo. */
        const val MIN_INTERVAL_MS = 120L

        /** A ~8 fps, tres frames son unos 375ms de quietud: se nota firme, no lento. */
        const val REQUIRED_STEADY_FRAMES = 3

        /** Fracción del ancho del código que el centro puede desplazarse entre frames. */
        const val MOVEMENT_TOLERANCE = 0.06f

        /** La cara impresa del DNI puede ser ~4% del ancho a distancia cómoda. */
        const val MIN_FACE_SIZE = 0.03f
    }
}
