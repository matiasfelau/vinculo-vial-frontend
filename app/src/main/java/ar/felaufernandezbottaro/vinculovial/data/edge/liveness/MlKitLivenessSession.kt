package ar.felaufernandezbottaro.vinculovial.data.edge.liveness

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import ar.felaufernandezbottaro.vinculovial.ExcludeFromCoverage
import ar.felaufernandezbottaro.vinculovial.domain.signup.LivenessSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CameraFrame
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.LivenessChallenge
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.LivenessProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Prueba de vida activa con ML Kit Face Detection sobre el preview.
 *
 * La idea central: **no alcanza con observar un gesto, hay que observar la transición**.
 * Si sólo se comprobara "está sonriendo", una foto impresa de alguien sonriendo pasaría.
 * Por eso la sesión primero fija un estado de partida (cara neutra / ojos abiertos) y
 * recién da por superado el desafío cuando ve el cambio en vivo.
 *
 * Los frames no se guardan: se procesan y se sueltan.
 */
@ExcludeFromCoverage(reason = "Cliente de ML Kit Face Detection: necesita los servicios de Google; la decisión del desafío vive en LivenessEvaluator")
@Singleton
class MlKitLivenessSession @Inject constructor() : LivenessSession {

    private val random: Random = Random.Default

    private val _progress = MutableStateFlow(LivenessProgress(challenge = LivenessChallenge.SMILE))
    override val progress: StateFlow<LivenessProgress> = _progress.asStateFlow()

    /** Un frame por vez: ML Kit no admite llamadas concurrentes sobre el mismo cliente. */
    private val inFlight = Mutex()
    private var lastAnalyzedAt = 0L
    private val evaluator = LivenessEvaluator()

    /**
     * NO es `by lazy`. La razón es la misma que en `MlKitDocumentScanSession.scanner`
     * (ver esa nota): esta sesión también es `@Singleton`, así que un detector cacheado
     * una sola vez para todo el proceso quedaría cerrado para siempre después de la
     * primera visita a la pantalla de selfie — [close] lo cierra al salir (correcto, para
     * no retener el detector con la cámara ya desvinculada), pero sin reconstrucción
     * explícita la segunda visita analizaría contra un cliente muerto, fallando en
     * silencio en el `catch` de [submitFrame]. Por eso se reconstruye en cada [start].
     */
    @Volatile
    private var detector: FaceDetector? = null

    private fun newDetector(): FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            // Tiempo real: el preview manda muchos frames y hay que seguirle el ritmo.
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            // Las probabilidades de sonrisa y ojos abiertos son justo el insumo del desafío.
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(MIN_FACE_SIZE)
            .build(),
    )

    override fun start() {
        // Por si start() se llamara dos veces sin un close() en el medio: así nunca queda
        // más de un cliente vivo a la vez.
        detector?.close()
        detector = newDetector()
        evaluator.reset()
        lastAnalyzedAt = 0L
        // El gesto se sortea en cada intento: uno fijo se podría grabar una sola vez y reusar.
        _progress.value = LivenessProgress(
            challenge = if (random.nextBoolean()) LivenessChallenge.SMILE else LivenessChallenge.BLINK,
        )
    }

    override suspend fun submitFrame(frame: CameraFrame) {
        if (_progress.value.isPassed) return
        // Cubre un frame que llega tarde desde el hilo del analyzer, después de que la
        // pantalla ya se fue y close() ya corrió.
        val client = detector ?: return

        val now = System.currentTimeMillis()
        if (now - lastAnalyzedAt < MIN_INTERVAL_MS) return
        // Si ya hay un frame en análisis se descarta este: interesa el más reciente.
        if (!inFlight.tryLock()) return

        try {
            lastAnalyzedAt = now
            val faces = client.process(frame.toInputImage()).await()
            _progress.value = evaluator.evaluate(_progress.value, faces.map { it.toObservation() }, frame.width, frame.height)
        } catch (e: Exception) {
            // Un frame que falla no invalida el intento: se ignora y sigue el siguiente.
        } finally {
            inFlight.unlock()
        }
    }

    override fun close() {
        detector?.close()
        detector = null
    }

    private fun Face.toObservation() = FaceObservation(
        boxWidth = boundingBox.width(),
        boxHeight = boundingBox.height(),
        headYawDegrees = headEulerAngleY,
        headRollDegrees = headEulerAngleZ,
        smilingProbability = smilingProbability,
        leftEyeOpenProbability = leftEyeOpenProbability,
        rightEyeOpenProbability = rightEyeOpenProbability,
    )

    private fun CameraFrame.toInputImage(): InputImage =
        InputImage.fromByteArray(
            nv21,
            width,
            height,
            rotationDegrees,
            InputImage.IMAGE_FORMAT_NV21,
        )

    private companion object {
        /** ~8 análisis por segundo: de sobra para un gesto y suave para la batería. */
        const val MIN_INTERVAL_MS = 120L

        const val MIN_FACE_SIZE = 0.15f
    }
}
