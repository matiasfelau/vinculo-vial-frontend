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
import com.vinculovial.app.domain.signup.model.DocumentScanMode
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
 * Escaneo en vivo del documento.
 *
 * En el frente busca cara y código de barras a la vez, para decidir sin preguntarle
 * nada al usuario si el DNI es el formato actual (PDF417 junto a la foto) o el
 * electrónico (sólo la foto; el código está en el reverso). En el reverso, si hace
 * falta, sólo busca el código.
 *
 * Igual que la prueba de vida, exige una lectura **estable** durante varios frames antes
 * de decidir: un solo frame borroso o mal encuadrado no alcanza para actuar. Confirmar
 * "no hay código" (DNI electrónico) pide más frames que confirmar "sí hay código" (DNI
 * actual) — confundir un código momentáneamente fuera de foco con su ausencia mandaría
 * al usuario a escanear un reverso que no hacía falta.
 *
 * Los frames se descartan a medida que se procesan; la sesión no acumula imágenes.
 */
@Singleton
class MlKitDocumentScanSession @Inject constructor() : DocumentScanSession {

    private val _progress = MutableStateFlow(DocumentScanProgress(mode = DocumentScanMode.FACE_AND_BARCODE))
    override val progress: StateFlow<DocumentScanProgress> = _progress.asStateFlow()

    /** Un frame por vez: ML Kit no admite llamadas concurrentes sobre el mismo cliente. */
    private val inFlight = Mutex()
    private var lastAnalyzedAt = 0L

    private var legacyStreak = 0
    private var electronicStreak = 0
    private var backBarcodeStreak = 0

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

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_PDF417, Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }

    override fun start(mode: DocumentScanMode) {
        legacyStreak = 0
        electronicStreak = 0
        backBarcodeStreak = 0
        lastAnalyzedAt = 0L
        _progress.value = DocumentScanProgress(mode = mode)
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
            // El código se busca en los dos modos; la cara sólo hace falta en el frente.
            val barcodes = barcodeScanner.process(input).await()

            _progress.value = when (_progress.value.mode) {
                DocumentScanMode.FACE_AND_BARCODE ->
                    evaluateFront(faceDetector.process(input).await(), barcodes, frame)

                DocumentScanMode.BARCODE_ONLY -> evaluateBack(barcodes)
            }
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

    private fun evaluateFront(faces: List<Face>, barcodes: List<Barcode>, frame: CameraFrame): DocumentScanProgress {
        val current = _progress.value

        if (faces.isEmpty()) {
            resetStreaks()
            return current.copy(faceDetected = false, barcodeDetected = false, hint = FramingHint.NO_FACE)
        }
        if (faces.size > 1) {
            resetStreaks()
            return current.copy(faceDetected = false, hint = FramingHint.MULTIPLE_FACES)
        }

        val coverage = faces.first().coverageOf(frame)
        framingHint(coverage)?.let { hint ->
            resetStreaks()
            return current.copy(faceDetected = true, barcodeDetected = false, hint = hint)
        }

        val hasBarcode = barcodes.any { it.rawValue != null }
        return if (hasBarcode) {
            electronicStreak = 0
            legacyStreak++
            current.copy(
                faceDetected = true,
                barcodeDetected = true,
                hint = FramingHint.NONE,
                outcome = if (legacyStreak >= LEGACY_CONFIRM_FRAMES) {
                    DocumentScanOutcome.LEGACY_WITH_BARCODE
                } else {
                    DocumentScanOutcome.NONE
                },
            )
        } else {
            legacyStreak = 0
            electronicStreak++
            current.copy(
                faceDetected = true,
                barcodeDetected = false,
                hint = FramingHint.NONE,
                outcome = if (electronicStreak >= ELECTRONIC_CONFIRM_FRAMES) {
                    DocumentScanOutcome.ELECTRONIC_NO_BARCODE
                } else {
                    DocumentScanOutcome.NONE
                },
            )
        }
    }

    private fun evaluateBack(barcodes: List<Barcode>): DocumentScanProgress {
        val current = _progress.value
        val hasBarcode = barcodes.any { it.rawValue != null }

        if (!hasBarcode) {
            backBarcodeStreak = 0
            return current.copy(barcodeDetected = false, hint = FramingHint.NONE)
        }

        backBarcodeStreak++
        return current.copy(
            barcodeDetected = true,
            hint = FramingHint.NONE,
            outcome = if (backBarcodeStreak >= BACK_BARCODE_CONFIRM_FRAMES) {
                DocumentScanOutcome.BARCODE_FOUND
            } else {
                DocumentScanOutcome.NONE
            },
        )
    }

    private fun resetStreaks() {
        legacyStreak = 0
        electronicStreak = 0
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
        const val LEGACY_CONFIRM_FRAMES = 3

        /**
         * Confirmación lenta: hay que estar seguros de que el código realmente no está
         * en el frente, no que el usuario todavía está acomodando el documento.
         */
        const val ELECTRONIC_CONFIRM_FRAMES = 10

        const val BACK_BARCODE_CONFIRM_FRAMES = 3
    }
}
