package ar.felaufernandezbottaro.vinculovial.data.edge.liveness

import ar.felaufernandezbottaro.vinculovial.domain.signup.model.FramingHint
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.LivenessChallenge
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.LivenessProgress
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.LivenessStage
import kotlin.math.abs

/**
 * Lo que la prueba de vida necesita saber de una cara detectada, sin depender de ML Kit:
 * [MlKitLivenessSession] la arma a partir de `Face` y delega la decisión en [LivenessEvaluator].
 *
 * Las probabilidades son `null` cuando el detector no las pudo calcular.
 */
internal data class FaceObservation(
    val boxWidth: Int,
    val boxHeight: Int,
    val headYawDegrees: Float,
    val headRollDegrees: Float,
    val smilingProbability: Float?,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
)

/**
 * La decisión de la prueba de vida activa: **no alcanza con observar un gesto, hay que observar la
 * transición**. Si sólo se comprobara "está sonriendo", una foto impresa de alguien sonriendo
 * pasaría; por eso primero se fija un estado de partida (cara sin el gesto) y recién se da por
 * superado el desafío cuando se ve el cambio en vivo.
 *
 * Es con estado (el estado de partida), así que hay una instancia por intento: se descarta con
 * [reset] o creando otra.
 */
internal class LivenessEvaluator {

    private var baselineEstablished = false

    fun reset() {
        baselineEstablished = false
    }

    fun evaluate(
        current: LivenessProgress,
        faces: List<FaceObservation>,
        frameWidth: Int,
        frameHeight: Int,
    ): LivenessProgress {
        if (faces.isEmpty()) {
            baselineEstablished = false
            return current.copy(
                stage = LivenessStage.SEARCHING_FACE,
                faceCoverage = 0f,
                hint = FramingHint.NO_FACE,
            )
        }
        if (faces.size > 1) {
            baselineEstablished = false
            return current.copy(
                stage = LivenessStage.SEARCHING_FACE,
                hint = FramingHint.MULTIPLE_FACES,
            )
        }

        val face = faces.first()
        val coverage = coverageOf(face, frameWidth, frameHeight)

        framingHint(face, coverage)?.let { hint ->
            baselineEstablished = false
            return current.copy(
                stage = LivenessStage.SEARCHING_FACE,
                faceCoverage = coverage,
                hint = hint,
            )
        }

        val gestureActive = isPerforming(face, current.challenge)

        // Estado de partida: la cara tiene que verse SIN el gesto antes de pedirlo. Esto
        // es lo que descarta una foto estática que ya viene sonriendo.
        if (!baselineEstablished) {
            baselineEstablished = !gestureActive
            return current.copy(
                stage = if (baselineEstablished) LivenessStage.WAITING_GESTURE else LivenessStage.SEARCHING_FACE,
                faceCoverage = coverage,
                hint = FramingHint.NONE,
            )
        }

        return current.copy(
            stage = if (gestureActive) LivenessStage.PASSED else LivenessStage.WAITING_GESTURE,
            faceCoverage = coverage,
            hint = FramingHint.NONE,
        )
    }

    /** `null` si el encuadre es aceptable. */
    private fun framingHint(face: FaceObservation, coverage: Float): FramingHint? = when {
        coverage < MIN_COVERAGE -> FramingHint.TOO_FAR
        coverage > MAX_COVERAGE -> FramingHint.TOO_CLOSE
        abs(face.headYawDegrees) > MAX_YAW_DEGREES -> FramingHint.LOOK_STRAIGHT
        abs(face.headRollDegrees) > MAX_ROLL_DEGREES -> FramingHint.LOOK_STRAIGHT
        else -> null
    }

    private fun isPerforming(face: FaceObservation, challenge: LivenessChallenge): Boolean = when (challenge) {
        LivenessChallenge.SMILE -> (face.smilingProbability ?: 0f) >= SMILE_THRESHOLD

        // Ambos ojos cerrados a la vez: un ojo solo suele ser ruido del detector.
        LivenessChallenge.BLINK ->
            (face.leftEyeOpenProbability ?: 1f) <= EYE_CLOSED_THRESHOLD &&
                (face.rightEyeOpenProbability ?: 1f) <= EYE_CLOSED_THRESHOLD
    }

    private fun coverageOf(face: FaceObservation, frameWidth: Int, frameHeight: Int): Float {
        val frameArea = (frameWidth.toLong() * frameHeight).toFloat()
        if (frameArea <= 0f) return 0f
        val faceArea = (face.boxWidth.toLong() * face.boxHeight).toFloat()
        return (faceArea / frameArea).coerceIn(0f, 1f)
    }

    internal companion object {
        const val MIN_COVERAGE = 0.06f
        const val MAX_COVERAGE = 0.60f
        const val MAX_YAW_DEGREES = 18f
        const val MAX_ROLL_DEGREES = 18f

        const val SMILE_THRESHOLD = 0.72f
        const val EYE_CLOSED_THRESHOLD = 0.25f
    }
}
