package ar.felaufernandezbottaro.vinculovial.presentation.benefits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ar.felaufernandezbottaro.vinculovial.domain.benefits.Benefit
import ar.felaufernandezbottaro.vinculovial.domain.benefits.BenefitError
import ar.felaufernandezbottaro.vinculovial.domain.benefits.benefit
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * El detalle de una promoción (US-18): qué muestra según tenga o no el código, qué callbacks
 * dispara cada botón y cada mensaje de error.
 */
@RunWith(RobolectricTestRunner::class)
// Pantalla alta: el contenido del detalle y del Home queda a la vista sin scrollear.
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class BenefitDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val without = benefit(
        id = "uno",
        partnerName = "Estación de prueba",
        title = "20 % en combustible",
        description = "Un descuento en tu próxima carga.",
        terms = "Válido una vez por persona.",
        endsAt = Instant.parse("2030-11-30T15:00:00Z"),
    )
    private val withCode = without.copy(code = "ABCD-1234")

    private var gotCode = 0
    private val copied = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private var backs = 0

    private fun show(
        selected: Benefit? = without,
        claim: ClaimState = ClaimState.Idle,
        loadBanner: suspend (String) -> ByteArray? = { null },
    ) {
        compose.setContent {
            VinculoVialTheme {
                BenefitDetailScreen(
                    state = BenefitsUiState(benefits = listOfNotNull(selected), selected = selected, claim = claim),
                    loadBanner = loadBanner,
                    onGetCode = { gotCode++ },
                    onCopy = { copied += it },
                    onOpen = { opened += it },
                    onBack = { backs++ },
                )
            }
        }
    }

    // -- Datos de la promoción ----------------------------------------------------------------

    @Test
    fun `muestra el titulo, el partner, la vigencia y la descripcion`() {
        show()

        compose.onNodeWithText("20 % en combustible").assertIsDisplayed()
        compose.onNodeWithText("Estación de prueba").assertIsDisplayed()
        compose.onNodeWithText("Hasta el 30/11").assertIsDisplayed()
        compose.onNodeWithText("Un descuento en tu próxima carga.").assertIsDisplayed()
    }

    @Test
    fun `las condiciones estan plegadas y se despliegan al tocarlas`() {
        show()

        compose.onNodeWithTag(TERMS_TEXT_TAG).assertDoesNotExist()
        compose.onNodeWithText("Condiciones").performClick()
        compose.onNodeWithTag(TERMS_TEXT_TAG).assertIsDisplayed().assertTextEquals("Válido una vez por persona.")

        compose.onNodeWithText("Condiciones").performClick()
        compose.onNodeWithTag(TERMS_TEXT_TAG).assertDoesNotExist()
    }

    @Test
    fun `el banner se pide por el id de la promocion`() {
        val requested = mutableListOf<String>()
        show(loadBanner = { id -> requested += id; null })

        compose.waitForIdle()

        assertEquals(listOf("uno"), requested)
    }

    @Test
    fun `volver dispara onBack`() {
        show()

        compose.onNodeWithContentDescription("Volver").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `sin promocion seleccionada no muestra nada de la promocion`() {
        show(selected = null)

        compose.onNodeWithText("Obtener código").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").assertIsDisplayed()
    }

    // -- Sin código ---------------------------------------------------------------------------

    @Test
    fun `sin codigo ofrece Obtener codigo y no muestra Copiar ni Abrir`() {
        show()

        compose.onNodeWithText("Obtener código").assertIsDisplayed()
        compose.onNodeWithText("Copiar").assertDoesNotExist()
        compose.onNodeWithText("Abrir").assertDoesNotExist()
        compose.onNodeWithTag(CODE_TAG).assertDoesNotExist()
    }

    @Test
    fun `Obtener codigo dispara onGetCode`() {
        show()

        compose.onNodeWithText("Obtener código").performClick()

        assertEquals(1, gotCode)
    }

    @Test
    fun `mientras se pide el codigo el boton queda deshabilitado, sin texto, con el indicador de carga`() {
        show(claim = ClaimState.Claiming)

        compose.onNodeWithText("Obtener código").assertDoesNotExist()
        compose.onNode(hasClickAction() and isNotEnabled()).assertExists()
        assertEquals(0, gotCode)
    }

    // -- Con código ---------------------------------------------------------------------------

    @Test
    fun `con codigo lo muestra en grande, con Copiar, Abrir y la indicacion`() {
        show(selected = withCode)

        compose.onNodeWithTag(CODE_TAG).assertIsDisplayed().assertTextEquals("ABCD-1234")
        compose.onNodeWithText("Copiar").assertIsDisplayed()
        compose.onNodeWithText("Abrir").assertIsDisplayed()
        compose.onNodeWithText("Ingresá el código en la aplicación de Estación de prueba").assertIsDisplayed()
        compose.onNodeWithText("Obtener código").assertDoesNotExist()
    }

    @Test
    fun `Copiar entrega el codigo y avisa Codigo copiado`() {
        show(selected = withCode)

        compose.onNodeWithText("Copiar").performClick()

        assertEquals(listOf("ABCD-1234"), copied)
        compose.onNodeWithText("Código copiado").assertIsDisplayed()
    }

    @Test
    fun `Abrir entrega el open_url del partner`() {
        show(selected = withCode)

        compose.onNodeWithText("Abrir").performClick()

        assertEquals(listOf("https://partner.example/app"), opened)
    }

    @Test
    fun `si el open_url no es https no se ofrece Abrir, pero si Copiar`() {
        show(selected = withCode.copy(openUrl = "http://partner.example/app"))

        compose.onNodeWithText("Abrir").assertDoesNotExist()
        compose.onNodeWithText("Copiar").assertIsDisplayed()
    }

    // -- Errores ------------------------------------------------------------------------------

    @Test
    fun `NO_CODES_LEFT muestra el mensaje y ya no ofrece pedir el codigo`() {
        show(claim = ClaimState.Failed(BenefitError.NoCodesLeft))

        compose.onNodeWithText("Se agotaron los códigos de esta promoción").assertIsDisplayed()
        compose.onNodeWithText("Obtener código").assertDoesNotExist()
    }

    @Test
    fun `BENEFIT_NOT_FOUND muestra el mensaje y ya no ofrece pedir el codigo`() {
        show(claim = ClaimState.Failed(BenefitError.NotFound))

        compose.onNodeWithText("Esta promoción ya no está disponible").assertIsDisplayed()
        compose.onNodeWithText("Obtener código").assertDoesNotExist()
    }

    @Test
    fun `un error transitorio muestra el mensaje y deja reintentar`() {
        show(claim = ClaimState.Failed(BenefitError.Unavailable))

        compose.onNodeWithTag(CLAIM_ERROR_TAG).assertIsDisplayed()
            .assertTextEquals(BenefitTexts.errorMessage(BenefitError.Unavailable))
        compose.onNodeWithText("Obtener código").performClick()

        assertEquals(1, gotCode)
    }

    @Test
    fun `sin error no se muestra ningun mensaje de error`() {
        show()

        compose.onNodeWithTag(CLAIM_ERROR_TAG).assertDoesNotExist()
    }
}
