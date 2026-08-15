package com.vinculovial.app.data.edge.document

import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.vinculovial.app.domain.signup.DocumentScanSession
import com.vinculovial.app.domain.signup.model.CameraFrame
import com.vinculovial.app.domain.signup.model.DocumentScanOutcome
import com.vinculovial.app.domain.signup.model.DocumentScanProgress
import com.vinculovial.app.domain.signup.model.FramingHint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Escaneo en vivo del frente del documento: busca cara y **PDF417** a la vez.
 *
 * Por ahora el MVP sólo acepta el DNI actual, así que no hay una segunda fase — si el
 * código no aparece después de unos segundos, [DocumentScanOutcome.NOT_FOUND_TIMEOUT] se
 * lo dice a la pantalla para que avise al usuario, en vez de quedarse escaneando para
 * siempre.
 *
 * Igual que la prueba de vida, exige una lectura **estable** durante varios frames antes
 * de decidir: un solo frame borroso o mal encuadrado no alcanza para actuar. Confirmar
 * "no hay código" pide bastantes más frames que confirmar "sí hay código": confundir un
 * código momentáneamente fuera de foco con su ausencia cortaría el escaneo de golpe.
 *
 * **La lectura exitosa manda por sobre la geometría.** Mientras no hay código, la
 * cobertura de la cara en el frame guía el encuadre (`TOO_FAR`/`TOO_CLOSE`). Pero apenas
 * el PDF417 decodifica, esa geometría deja de importar: el código es una señal binaria
 * (decodificó o no decodificó) y exigir además que el bounding box caiga en un rango de
 * cobertura "ideal" rechazaba capturas perfectamente legibles por un margen que no
 * calzaba con la heurística visual.
 *
 * Los frames se descartan a medida que se procesan; la sesión no acumula imágenes.
 */
@Singleton
class MlKitDocumentScanSession @Inject constructor() : DocumentScanSession {

    private val _progress = MutableStateFlow(DocumentScanProgress())
    override val progress: StateFlow<DocumentScanProgress> = _progress.asStateFlow()

    /** Un frame por vez: ML Kit no admite llamadas concurrentes sobre el mismo cliente. */
    private val inFlight = Mutex()
    private var lastAnalyzedAt = 0L

    private var foundStreak = 0
    private var notFoundStreak = 0

    private val faceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                // Tiempo real: hay que seguirle el ritmo al preview.
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(MIN_FACE_SIZE)
                .build(),
        )
    }

    /**
     * Sólo PDF417: el DNI electrónico (QR) queda fuera de alcance del MVP y no tiene
     * sentido detectarlo acá, donde la única salida posible sería "no lo sé leer".
     */
    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_PDF417)
                .build(),
        )
    }

    override fun start() {
        foundStreak = 0
        notFoundStreak = 0
        lastAnalyzedAt = 0L
        _progress.value = DocumentScanProgress()
    }

    override suspend fun submitFrame(frame: CameraFrame) {
        if (_progress.value.isDecided) return

        val now = System.currentTimeMillis()
        if (now - lastAnalyzedAt < MIN_INTERVAL_MS) return
        // Si ya hay un frame en análisis se descarta este: interesa el más reciente.
        if (!inFlight.tryLock()) return

        try {
            lastAnalyzedAt = now
            val input = frame.toInputImage()
            val barcodes = barcodeScanner.process(input).await()
            _progress.value = evaluate(faceDetector.process(input).await(), barcodes, frame)
        } catch (e: Exception) {
            // Un frame que falla no invalida el intento: se ignora y sigue el siguiente.
        } finally {
            inFlight.unlock()
        }
    }

    override fun close() {
        faceDetector.close()
        barcodeScanner.close()
    }

    private fun evaluate(faces: List<Face>, barcodes: List<Barcode>, frame: CameraFrame): DocumentScanProgress {
        val current = _progress.value

        if (faces.isEmpty()) {
            resetStreaks()
            return current.copy(faceDetected = false, barcodeDetected = false, hint = FramingHint.NO_FACE)
        }
        if (faces.size > 1) {
            resetStreaks()
            return current.copy(faceDetected = false, hint = FramingHint.MULTIPLE_FACES)
        }

        val hasBarcode = barcodes.any { it.rawValue != null }

        // El éxito de ML Kit hace override de la geometría: el PDF417 decodifica o no
        // decodifica, sin ambigüedad, y ya hay una cara detectada con confianza (ML Kit
        // filtra por MIN_FACE_SIZE antes de que el resultado llegue acá). Exigir además
        // que el bounding box caiga en un rango de cobertura "ideal" rechazaba capturas
        // perfectamente legibles sólo porque el margen no calzaba con la heurística visual.
        if (hasBarcode) {
            notFoundStreak = 0
            foundStreak++
            return current.copy(
                faceDetected = true,
                barcodeDetected = true,
                hint = FramingHint.NONE,
                outcome = if (foundStreak >= FOUND_CONFIRM_FRAMES) {
                    DocumentScanOutcome.FOUND
                } else {
                    DocumentScanOutcome.NONE
                },
            )
        }

        // Sin código todavía: acá sí importa la geometría, para guiar el encuadre
        // mientras se sigue buscando. Una vez que el código decodifica, deja de aplicar.
        val coverage = faces.first().coverageOf(frame)
        framingHint(coverage)?.let { hint ->
            resetStreaks()
            return current.copy(faceDetected = true, barcodeDetected = false, hint = hint)
        }

        foundStreak = 0
        notFoundStreak++
        return current.copy(
            faceDetected = true,
            barcodeDetected = false,
            hint = FramingHint.NONE,
            outcome = if (notFoundStreak >= NOT_FOUND_TIMEOUT_FRAMES) {
                DocumentScanOutcome.NOT_FOUND_TIMEOUT
            } else {
                DocumentScanOutcome.NONE
            },
        )
    }

    private fun resetStreaks() {
        foundStreak = 0
        notFoundStreak = 0
    }

    /** `null` si el encuadre es aceptable. */
    private fun framingHint(coverage: Float): FramingHint? = when {
        coverage < MIN_COVERAGE -> FramingHint.TOO_FAR
        coverage > MAX_COVERAGE -> FramingHint.TOO_CLOSE
        else -> null
    }

    private fun Face.coverageOf(frame: CameraFrame): Float {
        val frameArea = (frame.width.toLong() * frame.height).toFloat()
        if (frameArea <= 0f) return 0f
        val faceArea = (boundingBox.width().toLong() * boundingBox.height()).toFloat()
        return (faceArea / frameArea).coerceIn(0f, 1f)
    }

    private fun CameraFrame.toInputImage(): InputImage =
        InputImage.fromByteArray(nv21, width, height, rotationDegrees, InputImage.IMAGE_FORMAT_NV21)

    private companion object {
        /** ~8 análisis por segundo: de sobra para esta decisión y suave para la batería. */
        const val MIN_INTERVAL_MS = 120L

        /**
         * La foto impresa en el documento ocupa proporcionalmente menos que una selfie
         * de cara completa, así que el mínimo es más chico que el de liveness.
         */
        const val MIN_FACE_SIZE = 0.05f
        const val MIN_COVERAGE = 0.015f
        const val MAX_COVERAGE = 0.35f

        /** Confirmación rápida: es una señal positiva (el código ya se ve). */
        const val FOUND_CONFIRM_FRAMES = 3

        /**
         * "Unos segundos" antes de avisar que no hay código, para no confundir "fuera de
         * foco un instante" con "este documento no tiene PDF417". A ~8 fps, ~34 frames
         * son ~4 segundos.
         */
        const val NOT_FOUND_TIMEOUT_FRAMES = 34
    }
}
