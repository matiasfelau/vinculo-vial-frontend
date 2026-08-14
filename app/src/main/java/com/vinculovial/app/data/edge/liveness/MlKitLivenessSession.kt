package com.vinculovial.app.data.edge.liveness

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.vinculovial.app.domain.signup.LivenessSession
import com.vinculovial.app.domain.signup.model.LivenessChallenge
import com.vinculovial.app.domain.signup.model.LivenessFrame
import com.vinculovial.app.domain.signup.model.LivenessHint
import com.vinculovial.app.domain.signup.model.LivenessProgress
import com.vinculovial.app.domain.signup.model.LivenessStage
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
@Singleton
class MlKitLivenessSession @Inject constructor() : LivenessSession {

    private val random: Random = Random.Default

    private val _progress = MutableStateFlow(LivenessProgress(challenge = LivenessChallenge.SMILE))
    override val progress: StateFlow<LivenessProgress> = _progress.asStateFlow()

    /** Un frame por vez: ML Kit no admite llamadas concurrentes sobre el mismo cliente. */
    private val inFlight = Mutex()
    private var lastAnalyzedAt = 0L
    private var baselineEstablished = false

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                // Tiempo real: el preview manda muchos frames y hay que seguirle el ritmo.
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                // Las probabilidades de sonrisa y ojos abiertos son justo el insumo del desafío.
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(MIN_FACE_SIZE)
                .build(),
        )
    }

    override fun start() {
        baselineEstablished = false
        lastAnalyzedAt = 0L
        // El gesto se sortea en cada intento: uno fijo se podría grabar una sola vez y reusar.
        _progress.value = LivenessProgress(
            challenge = if (random.nextBoolean()) LivenessChallenge.SMILE else LivenessChallenge.BLINK,
        )
    }

    override suspend fun submitFrame(frame: LivenessFrame) {
        if (_progress.value.isPassed) return

        val now = System.currentTimeMillis()
        if (now - lastAnalyzedAt < MIN_INTERVAL_MS) return
        // Si ya hay un frame en análisis se descarta este: interesa el más reciente.
        if (!inFlight.tryLock()) return

        try {
            lastAnalyzedAt = now
            val faces = detector.process(frame.toInputImage()).await()
            _progress.value = evaluate(_progress.value, faces, frame)
        } catch (e: Exception) {
            // Un frame que falla no invalida el intento: se ignora y sigue el siguiente.
        } finally {
            inFlight.unlock()
        }
    }

    override fun close() {
        detector.close()
    }

    private fun evaluate(
        current: LivenessProgress,
        faces: List<Face>,
        frame: LivenessFrame,
    ): LivenessProgress {
        if (faces.isEmpty()) {
            baselineEstablished = false
            return current.copy(
                stage = LivenessStage.SEARCHING_FACE,
                faceCoverage = 0f,
                hint = LivenessHint.NO_FACE,
            )
        }
        if (faces.size > 1) {
            baselineEstablished = false
            return current.copy(
                stage = LivenessStage.SEARCHING_FACE,
                hint = LivenessHint.MULTIPLE_FACES,
            )
        }

        val face = faces.first()
        val coverage = face.coverageOf(frame)

        framingHint(face, coverage)?.let { hint ->
            baselineEstablished = false
            return current.copy(
                stage = LivenessStage.SEARCHING_FACE,
                faceCoverage = coverage,
                hint = hint,
            )
        }

        val gestureActive = face.isPerforming(current.challenge)

        // Estado de partida: la cara tiene que verse SIN el gesto antes de pedirlo. Esto
        // es lo que descarta una foto estática que ya viene sonriendo.
        if (!baselineEstablished) {
            baselineEstablished = !gestureActive
            return current.copy(
                stage = if (baselineEstablished) LivenessStage.WAITING_GESTURE else LivenessStage.SEARCHING_FACE,
                faceCoverage = coverage,
                hint = LivenessHint.NONE,
            )
        }

        return current.copy(
            stage = if (gestureActive) LivenessStage.PASSED else LivenessStage.WAITING_GESTURE,
            faceCoverage = coverage,
            hint = LivenessHint.NONE,
        )
    }

    /** `null` si el encuadre es aceptable. */
    private fun framingHint(face: Face, coverage: Float): LivenessHint? = when {
        coverage < MIN_COVERAGE -> LivenessHint.TOO_FAR
        coverage > MAX_COVERAGE -> LivenessHint.TOO_CLOSE
        kotlin.math.abs(face.headEulerAngleY) > MAX_YAW_DEGREES -> LivenessHint.LOOK_STRAIGHT
        kotlin.math.abs(face.headEulerAngleZ) > MAX_ROLL_DEGREES -> LivenessHint.LOOK_STRAIGHT
        else -> null
    }

    private fun Face.isPerforming(challenge: LivenessChallenge): Boolean = when (challenge) {
        LivenessChallenge.SMILE -> (smilingProbability ?: 0f) >= SMILE_THRESHOLD

        // Ambos ojos cerrados a la vez: un ojo solo suele ser ruido del detector.
        LivenessChallenge.BLINK ->
            (leftEyeOpenProbability ?: 1f) <= EYE_CLOSED_THRESHOLD &&
                (rightEyeOpenProbability ?: 1f) <= EYE_CLOSED_THRESHOLD
    }

    private fun Face.coverageOf(frame: LivenessFrame): Float {
        val frameArea = (frame.width.toLong() * frame.height).toFloat()
        if (frameArea <= 0f) return 0f
        val faceArea = (boundingBox.width().toLong() * boundingBox.height()).toFloat()
        return (faceArea / frameArea).coerceIn(0f, 1f)
    }

    private fun LivenessFrame.toInputImage(): InputImage =
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
        const val MIN_COVERAGE = 0.06f
        const val MAX_COVERAGE = 0.60f
        const val MAX_YAW_DEGREES = 18f
        const val MAX_ROLL_DEGREES = 18f

        const val SMILE_THRESHOLD = 0.72f
        const val EYE_CLOSED_THRESHOLD = 0.25f
    }
}
