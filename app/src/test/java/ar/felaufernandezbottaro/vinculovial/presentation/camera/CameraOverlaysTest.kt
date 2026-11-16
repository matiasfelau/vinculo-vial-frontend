package ar.felaufernandezbottaro.vinculovial.presentation.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentScanStage
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.FramingHint
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.LivenessChallenge
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.LivenessProgress
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.LivenessStage
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lo que se ve sobre la cámara en el escaneo del DNI y en la selfie con prueba de vida: los
 * textos de cada etapa, los botones y qué se bloquea mientras la captura está en curso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class CameraOverlaysTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun showDocument(stage: DocumentScanStage, failure: String? = null, torch: Boolean = false) {
        compose.setContent {
            VinculoVialTheme {
                DocumentScanOverlay(
                    stage = stage,
                    failure = failure,
                    torchOn = torch,
                    onTorchToggle = { calls += "torch" },
                    onCancel = { calls += "cancel" },
                    preview = { Box(Modifier.fillMaxSize()) },
                )
            }
        }
    }

    private fun showLiveness(progress: LivenessProgress, failure: String? = null, locked: Boolean = false) {
        compose.setContent {
            VinculoVialTheme {
                LivenessCaptureOverlay(
                    progress = progress,
                    failure = failure,
                    locked = locked,
                    onCancel = { calls += "cancel" },
                    preview = { Box(Modifier.fillMaxSize()) },
                )
            }
        }
    }

    // -- Escaneo del DNI ----------------------------------------------------------------------

    @Test
    fun `buscando pide apuntar al codigo y da la pista de distancia`() {
        showDocument(DocumentScanStage.SEARCHING)

        compose.onNodeWithText("Escaneá el código de tu DNI").assertIsDisplayed()
        compose.onNodeWithText("Apuntá al código de tu DNI").assertIsDisplayed()
        compose.onNodeWithText("Sostenelo a una distancia cómoda, donde se vea nítido").assertIsDisplayed()
    }

    @Test
    fun `las lecturas transitorias se muestran igual que buscando y nunca piden acercarse`() {
        val transitory = listOf(
            DocumentScanStage.NOT_A_DNI,
            DocumentScanStage.UNSUPPORTED_DOCUMENT,
            DocumentScanStage.MISSING_PROCEDURE_NUMBER,
        )

        transitory.forEach { stage ->
            assertEquals("Apuntá al código de tu DNI", stage.headline())
            assertEquals("Sostenelo a una distancia cómoda, donde se vea nítido", stage.detail())
        }
    }

    @Test
    fun `con el codigo detectado dice enfocando y sin detalle`() {
        assertEquals("Enfocando…", DocumentScanStage.CODE_DETECTED.headline())
        assertNull(DocumentScanStage.CODE_DETECTED.detail())
        assertEquals("¡Listo!", DocumentScanStage.DECODED.headline())
        assertNull(DocumentScanStage.DECODED.detail())

        showDocument(DocumentScanStage.DECODED)
        compose.onNodeWithText("¡Listo!").assertIsDisplayed()
        compose.onNodeWithText("Sostenelo a una distancia cómoda, donde se vea nítido").assertDoesNotExist()
    }

    @Test
    fun `una falla de la camara reemplaza al titular y esconde el detalle`() {
        showDocument(DocumentScanStage.SEARCHING, failure = "No pudimos abrir la cámara.")

        compose.onNodeWithText("No pudimos abrir la cámara.").assertIsDisplayed()
        compose.onNodeWithText("Apuntá al código de tu DNI").assertDoesNotExist()
        compose.onNodeWithText("Sostenelo a una distancia cómoda, donde se vea nítido").assertDoesNotExist()
    }

    @Test
    fun `cancelar y la linterna del DNI disparan sus callbacks`() {
        showDocument(DocumentScanStage.SEARCHING)

        compose.onNodeWithContentDescription("Encender la luz").performClick()
        compose.onNodeWithContentDescription("Cancelar").performClick()

        assertEquals(listOf("torch", "cancel"), calls)
    }

    @Test
    fun `con la linterna prendida ofrece apagarla`() {
        showDocument(DocumentScanStage.SEARCHING, torch = true)

        compose.onNodeWithContentDescription("Apagar la luz").assertIsDisplayed()
    }

    // -- Selfie con prueba de vida ------------------------------------------------------------

    @Test
    fun `cada pista de encuadre tiene su instruccion`() {
        fun text(hint: FramingHint) =
            LivenessProgress(challenge = LivenessChallenge.SMILE, hint = hint).instructionText()

        assertEquals("Poné tu cara dentro del óvalo", text(FramingHint.NO_FACE))
        assertEquals("Que sólo se vea tu cara", text(FramingHint.MULTIPLE_FACES))
        assertEquals("Acercate un poco", text(FramingHint.TOO_FAR))
        assertEquals("Alejate un poco", text(FramingHint.TOO_CLOSE))
        assertEquals("Mirá de frente a la cámara", text(FramingHint.LOOK_STRAIGHT))
    }

    @Test
    fun `la instruccion depende de la etapa y del gesto sorteado`() {
        fun text(challenge: LivenessChallenge, stage: LivenessStage) =
            LivenessProgress(challenge = challenge, stage = stage).instructionText()

        assertEquals("¡Listo!", text(LivenessChallenge.SMILE, LivenessStage.PASSED))
        assertEquals("Ahora sonreí", text(LivenessChallenge.SMILE, LivenessStage.WAITING_GESTURE))
        assertEquals("Ahora parpadeá", text(LivenessChallenge.BLINK, LivenessStage.WAITING_GESTURE))
        assertEquals("Mantené cara neutra un segundo", text(LivenessChallenge.SMILE, LivenessStage.SEARCHING_FACE))
        assertEquals(
            "Mantené los ojos abiertos un segundo",
            text(LivenessChallenge.BLINK, LivenessStage.SEARCHING_FACE),
        )
    }

    @Test
    fun `muestra la instruccion del desafio`() {
        showLiveness(LivenessProgress(challenge = LivenessChallenge.BLINK, stage = LivenessStage.WAITING_GESTURE))

        compose.onNodeWithText("Ahora parpadeá").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancelar").assertIsEnabled()
    }

    @Test
    fun `una falla reemplaza a la instruccion`() {
        showLiveness(
            LivenessProgress(challenge = LivenessChallenge.SMILE),
            failure = "No pudimos tomar la foto. Intentá de nuevo.",
        )

        compose.onNodeWithText("No pudimos tomar la foto. Intentá de nuevo.").assertIsDisplayed()
    }

    @Test
    fun `con la captura en curso no se puede cancelar`() {
        showLiveness(
            LivenessProgress(challenge = LivenessChallenge.SMILE, stage = LivenessStage.PASSED),
            locked = true,
        )

        compose.onNodeWithContentDescription("Cancelar").assertIsNotEnabled()
        compose.onNodeWithText("¡Listo!").assertIsDisplayed()
    }

    @Test
    fun `cancelar antes de la captura avisa`() {
        showLiveness(LivenessProgress(challenge = LivenessChallenge.SMILE))

        compose.onNodeWithContentDescription("Cancelar").performClick()

        assertEquals(listOf("cancel"), calls)
    }
}
