package ar.felaufernandezbottaro.vinculovial.presentation.terms

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import ar.felaufernandezbottaro.vinculovial.data.terms.RawTermsSource
import ar.felaufernandezbottaro.vinculovial.domain.terms.GetTermsUseCase
import ar.felaufernandezbottaro.vinculovial.domain.terms.LegalInfo
import ar.felaufernandezbottaro.vinculovial.domain.terms.TermsBlock
import ar.felaufernandezbottaro.vinculovial.domain.terms.TermsSpan
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * La pantalla "Términos y Condiciones" (US-19) con el texto REAL de `res/raw/terminos.md`: se ve
 * el documento completo y los marcadores quedan reemplazados por los datos configurados.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class TermsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val legal = LegalInfo(
        responsibleName = "Vinculo Vial SAS de prueba",
        contactEmail = "derechos@vinculovial.example",
        address = "Av. Siempreviva 742, CABA",
        version = "1.0",
    )

    private var backs = 0
    private var retries = 0

    private fun realDocument(): List<TermsBlock> = runBlocking {
        GetTermsUseCase(RawTermsSource(RuntimeEnvironment.getApplication(), Dispatchers.Unconfined), legal)()
    }

    private fun show(state: TermsUiState) {
        compose.setContent {
            VinculoVialTheme {
                TermsScreen(state = state, onRetry = { retries++ }, onBack = { backs++ })
            }
        }
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag(DOCUMENT_TAG).performScrollToNode(hasText(text, substring = true))
    }

    // -- El texto real ------------------------------------------------------------------------

    @Test
    fun `el archivo del texto trae los cuatro marcadores y 14 secciones`() {
        val raw = runBlocking { RawTermsSource(RuntimeEnvironment.getApplication(), Dispatchers.Unconfined).markdown() }

        listOf("{{VERSION}}", "{{RESPONSABLE}}", "{{DOMICILIO}}", "{{EMAIL_CONTACTO}}").forEach {
            assertTrue("falta $it", it in raw)
        }
        assertEquals(14, Regex("(?m)^## \\d+\\. ").findAll(raw).count())
    }

    @Test
    fun `el documento armado no deja ningun marcador sin reemplazar`() {
        val blocks = realDocument()

        val allText = blocks.flatMap { block ->
            when (block) {
                is TermsBlock.Heading -> listOf(block.text)
                is TermsBlock.Paragraph -> block.spans.map(TermsSpan::text)
                is TermsBlock.Bullets -> block.items.flatten().map(TermsSpan::text)
                is TermsBlock.Table -> block.headers + block.rows.flatten().flatten().map(TermsSpan::text)
            }
        }
        assertFalse(allText.any { "{{" in it })
        assertTrue(allText.any { "Vinculo Vial SAS de prueba" in it })
    }

    @Test
    fun `muestra el titulo y la version`() {
        show(TermsUiState.Ready(realDocument()))

        compose.onNodeWithText("Términos y Condiciones y Política de Privacidad de Vinculo Vial").assertIsDisplayed()
        compose.onNodeWithText("Versión 1.0").assertIsDisplayed()
    }

    @Test
    fun `muestra los datos del responsable configurados, no los marcadores`() {
        show(TermsUiState.Ready(realDocument()))

        compose.onNodeWithText("Vinculo Vial SAS de prueba", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Av. Siempreviva 742, CABA", substring = true).assertIsDisplayed()
        compose.onNodeWithText("derechos@vinculovial.example", substring = true).assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("{{", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun `se llega a todas las secciones, de la primera a la ultima`() {
        show(TermsUiState.Ready(realDocument()))

        listOf(
            "1. Quiénes somos y aceptación",
            "5. Uso permitido y prohibido",
            "9. Datos personales",
            "10. Tus derechos",
            "14. Ley aplicable",
        ).forEach { heading ->
            scrollTo(heading)
            compose.onNodeWithText(heading).assertIsDisplayed()
        }
    }

    @Test
    fun `las listas se ven con viñetas y las negritas como texto`() {
        show(TermsUiState.Ready(realDocument()))

        scrollTo("No es un servicio de emergencias.")
        compose.onNodeWithText("No es un servicio de emergencias.", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("•").onFirst().assertExists()
    }

    @Test
    fun `la tabla de datos personales no pierde ninguna fila`() {
        show(TermsUiState.Ready(realDocument()))

        listOf("Patentes vinculadas", "Fotos de los avisos", "Valoraciones y reportes").forEach { dato ->
            scrollTo(dato)
            compose.onNodeWithText(dato, substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun `la leyenda de la Disposicion 10 2008 y el plazo de acceso estan`() {
        show(TermsUiState.Ready(realDocument()))

        scrollTo("AGENCIA DE ACCESO A LA INFORMACIÓN PÚBLICA")
        compose.onNodeWithText("AGENCIA DE ACCESO A LA INFORMACIÓN PÚBLICA", substring = true).assertIsDisplayed()
        scrollTo("10 días corridos")
        compose.onNodeWithText("10 días corridos", substring = true).assertIsDisplayed()
    }

    // -- Estados y callbacks ------------------------------------------------------------------

    @Test
    fun `volver dispara onBack`() {
        show(TermsUiState.Ready(emptyList()))

        compose.onNodeWithContentDescription("Volver").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `si el texto no se pudo leer avisa y deja reintentar`() {
        show(TermsUiState.Failed)

        compose.onNodeWithText("No pudimos mostrar los términos. Intentá de nuevo.").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `el titulo de la barra es Terminos y Condiciones`() {
        show(TermsUiState.Loading)

        compose.onNodeWithText("Términos y Condiciones").assertIsDisplayed()
    }
}
