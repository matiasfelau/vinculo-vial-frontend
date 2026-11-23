package ar.felaufernandezbottaro.vinculovial.presentation.scan

import android.graphics.Bitmap
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
import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidenceRole
import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidenceStep
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La captura guiada de evidencia sobre la cámara: la instrucción y el contador de cada paso, el
 * obturador, la revisión de la foto (repetir, agregar otra, continuar o terminar) y el diálogo de
 * salida, que descarta las fotos sólo si el usuario lo confirma.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class EvidenceCaptureOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val general = EvidenceStep(EvidenceRole.GENERAL, "Sacá una foto del auto completo")
    private val plate = EvidenceStep(EvidenceRole.PLATE, "Sacá una foto de la patente")
    private val twoSteps = EvidenceCaptureState(steps = listOf(general, plate), phase = EvidencePhase.CAPTURING)

    private fun show(
        state: EvidenceCaptureState = twoSteps,
        cameraFailure: String? = null,
        reviewBitmap: Bitmap? = null,
        shutterEnabled: Boolean = true,
        showCancelDialog: Boolean = false,
        gallery: (() -> Unit)? = null,
    ) {
        compose.setContent {
            VinculoVialTheme {
                EvidenceCaptureOverlay(
                    state = state,
                    cameraFailure = cameraFailure,
                    reviewBitmap = reviewBitmap,
                    shutterEnabled = shutterEnabled,
                    showCancelDialog = showCancelDialog,
                    onBack = { calls += "back" },
                    onCancelDialogDismissed = { calls += "dialogDismissed" },
                    onCancelConfirmed = { calls += "cancelConfirmed" },
                    onShutter = { calls += "shutter" },
                    onPickFromGallery = gallery,
                    onPhotoRetake = { calls += "retake" },
                    onAddAnotherPhoto = { calls += "addAnother" },
                    onStepConfirmed = { calls += "confirmed" },
                    preview = { Box(Modifier.fillMaxSize()) },
                )
            }
        }
    }

    @Test
    fun `muestra la instruccion y el contador del paso`() {
        show()

        compose.onNodeWithText("Sacá una foto del auto completo").assertIsDisplayed()
        compose.onNodeWithText("Paso 1 de 2").assertIsDisplayed()
    }

    @Test
    fun `un paso que admite varias fotos cuenta tambien la foto`() {
        val multi = EvidenceCaptureState(
            steps = listOf(EvidenceStep(EvidenceRole.GENERAL, "Sacá fotos", maxPhotos = 3)),
            photoIndex = 1,
            phase = EvidencePhase.CAPTURING,
        )

        assertEquals("Paso 1 de 1 · foto 2 de hasta 3", stepCounter(multi))
        assertEquals("Paso 1 de 2", stepCounter(twoSteps))
        assertEquals("", stepCounter(EvidenceCaptureState()))
    }

    @Test
    fun `el obturador saca la foto`() {
        show()

        compose.onNodeWithContentDescription("Sacar foto").assertIsEnabled().performClick()

        assertEquals(listOf("shutter"), calls)
    }

    @Test
    fun `sin la camara lista el obturador esta deshabilitado`() {
        show(shutterEnabled = false)

        compose.onNodeWithContentDescription("Sacar foto").assertIsNotEnabled()
    }

    @Test
    fun `la galeria solo aparece si hay selector y lo dispara`() {
        show()
        compose.onNodeWithText("Elegir de la galería").assertDoesNotExist()
    }

    @Test
    fun `con selector de galeria ofrece elegir una foto`() {
        show(gallery = { calls += "gallery" })

        compose.onNodeWithText("Elegir de la galería").performClick()

        assertEquals(listOf("gallery"), calls)
    }

    @Test
    fun `una falla de la camara se muestra`() {
        show(cameraFailure = "No pudimos abrir la cámara.")

        compose.onNodeWithText("No pudimos abrir la cámara.").assertIsDisplayed()
    }

    @Test
    fun `un error de la foto muestra su mensaje`() {
        show(twoSteps.copy(error = EvidenceError.TOO_LARGE))

        compose.onNodeWithText(EvidenceError.TOO_LARGE.message()).assertIsDisplayed()
    }

    @Test
    fun `cada error de la foto tiene un mensaje distinto`() {
        val messages = EvidenceError.entries.map { it.message() }

        assertEquals(messages.size, messages.toSet().size)
    }

    @Test
    fun `procesando muestra el aviso y no deja volver`() {
        show(twoSteps.copy(phase = EvidencePhase.PROCESSING))

        compose.onNodeWithText("Procesando la foto…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver al chat").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Sacar foto").assertDoesNotExist()
    }

    @Test
    fun `volver abre el dialogo de salida`() {
        show()

        compose.onNodeWithContentDescription("Volver al chat").performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    fun `el dialogo de salida confirma o sigue sacando`() {
        show(showCancelDialog = true)

        compose.onNodeWithText("Las fotos que sacaste se descartan y vas a tener que sacarlas de nuevo.")
            .assertIsDisplayed()
        compose.onNodeWithText("Sí, volver").performClick()
        compose.onNodeWithText("Seguir sacando").performClick()

        assertEquals(listOf("cancelConfirmed", "dialogDismissed"), calls)
    }

    @Test
    fun `revisando un paso intermedio ofrece repetir y continuar`() {
        show(twoSteps.copy(phase = EvidencePhase.REVIEWING))

        compose.onNodeWithText("Repetir foto").performClick()
        compose.onNodeWithText("Continuar").performClick()
        compose.onNodeWithText("Agregar otra").assertDoesNotExist()

        assertEquals(listOf("retake", "confirmed"), calls)
    }

    @Test
    fun `revisando el ultimo paso ofrece terminar`() {
        show(twoSteps.copy(stepIndex = 1, phase = EvidencePhase.REVIEWING))

        compose.onNodeWithText("Terminar").performClick()

        assertEquals(listOf("confirmed"), calls)
    }

    @Test
    fun `un paso que admite otra foto ofrece agregarla`() {
        val state = EvidenceCaptureState(
            steps = listOf(EvidenceStep(EvidenceRole.GENERAL, "Sacá fotos", maxPhotos = 2)),
            phase = EvidencePhase.REVIEWING,
        )
        show(state)

        compose.onNodeWithText("Agregar otra").performClick()

        assertEquals(listOf("addAnother"), calls)
    }

    @Test
    fun `revisando muestra la foto sacada`() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        show(twoSteps.copy(phase = EvidencePhase.REVIEWING), reviewBitmap = bitmap)

        compose.onNodeWithContentDescription("Foto sacada").assertIsDisplayed()
    }

    @Test
    fun `sin pasos no hay instruccion`() {
        show(EvidenceCaptureState(phase = EvidencePhase.CAPTURING))

        compose.onNodeWithText("Paso 1 de 2").assertDoesNotExist()
    }
}
