package ar.felaufernandezbottaro.vinculovial.data.edge.liveness

import ar.felaufernandezbottaro.vinculovial.domain.signup.model.FramingHint
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.LivenessChallenge
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.LivenessProgress
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.LivenessStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La decisión de la prueba de vida: lo que importa es la **transición** (primero la cara sin el
 * gesto, después el gesto), para que una foto impresa que ya viene sonriendo no pase.
 */
class LivenessEvaluatorTest {

    private val evaluator = LivenessEvaluator()
    private val frameW = 1000
    private val frameH = 1000

    private fun smileProgress() = LivenessProgress(challenge = LivenessChallenge.SMILE)
    private fun blinkProgress() = LivenessProgress(challenge = LivenessChallenge.BLINK)

    /** Una cara que ocupa ~25 % del cuadro, de frente, neutra: encuadre aceptable. */
    private fun face(
        width: Int = 500,
        height: Int = 500,
        yaw: Float = 0f,
        roll: Float = 0f,
        smile: Float? = 0.05f,
        left: Float? = 0.95f,
        right: Float? = 0.95f,
    ) = FaceObservation(width, height, yaw, roll, smile, left, right)

    private fun eval(progress: LivenessProgress, vararg faces: FaceObservation) =
        evaluator.evaluate(progress, faces.toList(), frameW, frameH)

    @Test
    fun `sin cara pide poner la cara dentro del ovalo y la cobertura es cero`() {
        val result = eval(smileProgress().copy(faceCoverage = 0.3f))

        assertEquals(LivenessStage.SEARCHING_FACE, result.stage)
        assertEquals(FramingHint.NO_FACE, result.hint)
        assertEquals(0f, result.faceCoverage, 0f)
    }

    @Test
    fun `mas de una cara no avanza`() {
        val result = eval(smileProgress(), face(), face())

        assertEquals(LivenessStage.SEARCHING_FACE, result.stage)
        assertEquals(FramingHint.MULTIPLE_FACES, result.hint)
    }

    @Test
    fun `una cara muy lejos pide acercarse y informa la cobertura`() {
        val result = eval(smileProgress(), face(width = 100, height = 100))

        assertEquals(FramingHint.TOO_FAR, result.hint)
        assertEquals(0.01f, result.faceCoverage, 0.0001f)
        assertEquals(LivenessStage.SEARCHING_FACE, result.stage)
    }

    @Test
    fun `una cara muy cerca pide alejarse`() {
        val result = eval(smileProgress(), face(width = 900, height = 900))

        assertEquals(FramingHint.TOO_CLOSE, result.hint)
    }

    @Test
    fun `una cara girada o inclinada pide mirar de frente`() {
        assertEquals(FramingHint.LOOK_STRAIGHT, eval(smileProgress(), face(yaw = 25f)).hint)
        assertEquals(FramingHint.LOOK_STRAIGHT, eval(smileProgress(), face(yaw = -25f)).hint)
        assertEquals(FramingHint.LOOK_STRAIGHT, eval(smileProgress(), face(roll = 30f)).hint)
    }

    @Test
    fun `un cuadro sin area no divide por cero`() {
        val result = evaluator.evaluate(smileProgress(), listOf(face()), 0, 0)

        assertEquals(0f, result.faceCoverage, 0f)
        assertEquals(FramingHint.TOO_FAR, result.hint)
    }

    @Test
    fun `la cobertura nunca pasa de uno`() {
        val result = eval(smileProgress(), face(width = 5000, height = 5000))

        assertEquals(1f, result.faceCoverage, 0f)
    }

    @Test
    fun `la cara neutra fija el estado de partida y recien despues se espera el gesto`() {
        val result = eval(smileProgress(), face(smile = 0.1f))

        assertEquals(LivenessStage.WAITING_GESTURE, result.stage)
        assertEquals(FramingHint.NONE, result.hint)
        assertEquals(0.25f, result.faceCoverage, 0.0001f)
    }

    @Test
    fun `una foto que ya viene sonriendo no fija el estado de partida y nunca pasa`() {
        val first = eval(smileProgress(), face(smile = 0.95f))
        assertEquals(LivenessStage.SEARCHING_FACE, first.stage)

        val second = eval(first, face(smile = 0.95f))
        assertEquals("sin baseline la sonrisa no alcanza", LivenessStage.SEARCHING_FACE, second.stage)
    }

    @Test
    fun `sonreir despues de la cara neutra supera el desafio`() {
        val baseline = eval(smileProgress(), face(smile = 0.1f))
        val waiting = eval(baseline, face(smile = 0.3f))
        val passed = eval(waiting, face(smile = 0.9f))

        assertEquals(LivenessStage.WAITING_GESTURE, waiting.stage)
        assertEquals(LivenessStage.PASSED, passed.stage)
        assertTrue(passed.isPassed)
    }

    @Test
    fun `perder la cara descarta el estado de partida`() {
        val baseline = eval(smileProgress(), face(smile = 0.1f))
        eval(baseline) // sin cara
        val afterLoss = eval(baseline, face(smile = 0.95f))

        assertEquals(
            "tras perder la cara hay que volver a ver la cara neutra antes del gesto",
            LivenessStage.SEARCHING_FACE,
            afterLoss.stage,
        )
    }

    @Test
    fun `un encuadre malo tambien descarta el estado de partida`() {
        val baseline = eval(smileProgress(), face(smile = 0.1f))
        eval(baseline, face(yaw = 40f))
        val afterBadFraming = eval(baseline, face(smile = 0.95f))

        assertEquals(LivenessStage.SEARCHING_FACE, afterBadFraming.stage)
    }

    @Test
    fun `reset descarta el estado de partida de un intento anterior`() {
        eval(smileProgress(), face(smile = 0.1f))
        evaluator.reset()

        val result = eval(smileProgress(), face(smile = 0.95f))

        assertEquals(LivenessStage.SEARCHING_FACE, result.stage)
    }

    @Test
    fun `parpadear requiere los dos ojos cerrados a la vez`() {
        val baseline = eval(blinkProgress(), face())
        val oneEye = eval(baseline, face(left = 0.05f, right = 0.9f))
        val bothEyes = eval(baseline, face(left = 0.05f, right = 0.1f))

        assertEquals("un ojo solo es ruido del detector", LivenessStage.WAITING_GESTURE, oneEye.stage)
        assertEquals(LivenessStage.PASSED, bothEyes.stage)
    }

    @Test
    fun `sin probabilidades ni el parpadeo ni la sonrisa se dan por hechos`() {
        val blinkBase = eval(blinkProgress(), face(left = null, right = null))
        assertEquals("ojos desconocidos cuentan como abiertos", LivenessStage.WAITING_GESTURE, blinkBase.stage)
        assertEquals(LivenessStage.WAITING_GESTURE, eval(blinkBase, face(left = null, right = null)).stage)

        evaluator.reset()
        val smileBase = eval(smileProgress(), face(smile = null))
        assertEquals(LivenessStage.WAITING_GESTURE, smileBase.stage)
        assertEquals(LivenessStage.WAITING_GESTURE, eval(smileBase, face(smile = null)).stage)
    }

    @Test
    fun `conserva el desafio sorteado en cada paso`() {
        val result = eval(blinkProgress(), face())

        assertEquals(LivenessChallenge.BLINK, result.challenge)
    }
}
