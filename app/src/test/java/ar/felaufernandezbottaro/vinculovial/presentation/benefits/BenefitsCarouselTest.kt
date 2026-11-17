package ar.felaufernandezbottaro.vinculovial.presentation.benefits

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.swipeLeft
import ar.felaufernandezbottaro.vinculovial.domain.benefits.benefit
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El carrusel del Home (US-18): se ve con las promociones, no deja rastro sin ellas, avanza solo
 * cada 5 s, se detiene mientras se lo toca y abre el detalle de la promoción que se toca.
 */
@RunWith(RobolectricTestRunner::class)
// Pantalla alta: el contenido del detalle y del Home queda a la vista sin scrollear.
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class BenefitsCarouselTest {

    @get:Rule
    val compose = createComposeRule()

    private val uno = benefit(id = "uno", title = "Uno")
    private val dos = benefit(id = "dos", title = "Dos")
    private val tres = benefit(id = "tres", title = "Tres")

    private fun show(
        benefits: List<ar.felaufernandezbottaro.vinculovial.domain.benefits.Benefit>,
        onOpen: (String) -> Unit = {},
        loadBanner: suspend (String) -> ByteArray? = { null },
    ) {
        compose.setContent {
            VinculoVialTheme {
                BenefitsCarousel(benefits = benefits, loadBanner = loadBanner, onOpenBenefit = onOpen)
            }
        }
    }

    private fun indicator() = compose.onNodeWithTag(INDICATOR_TAG)

    private fun assertPage(number: Int, of: Int) {
        indicator().assert(hasContentDescription("Promoción $number de $of"))
    }

    @Test
    fun `sin promociones no dibuja nada`() {
        show(emptyList())

        compose.onNodeWithTag(CAROUSEL_TAG).assertDoesNotExist()
        compose.onNodeWithTag(INDICATOR_TAG).assertDoesNotExist()
    }

    @Test
    fun `con promociones muestra el carrusel y la primera, con el titulo mientras no llega el banner`() {
        show(listOf(uno, dos))

        compose.onNodeWithTag(CAROUSEL_TAG).assertIsDisplayed()
        compose.onNodeWithTag("${BENEFIT_ITEM_TAG}uno").assertIsDisplayed()
        compose.onNodeWithText("Uno").assertIsDisplayed()
    }

    @Test
    fun `con una sola promocion no hay indicador de pagina`() {
        show(listOf(uno))

        compose.onNodeWithTag("${BENEFIT_ITEM_TAG}uno").assertIsDisplayed()
        indicator().assertDoesNotExist()
    }

    @Test
    fun `el indicador dice en que pagina esta`() {
        show(listOf(uno, dos, tres))

        assertPage(1, of = 3)
    }

    @Test
    fun `avanza solo cada 5 segundos y da la vuelta al final`() {
        compose.mainClock.autoAdvance = false
        show(listOf(uno, dos, tres))

        compose.mainClock.advanceTimeBy(4_900)
        compose.waitForIdle()
        assertPage(1, of = 3)

        compose.mainClock.advanceTimeBy(1_500)
        assertPage(2, of = 3)

        compose.mainClock.advanceTimeBy(5_500)
        assertPage(3, of = 3)

        compose.mainClock.advanceTimeBy(5_500)
        assertPage(1, of = 3)
    }

    @Test
    fun `con una sola promocion no avanza`() {
        compose.mainClock.autoAdvance = false
        show(listOf(uno))

        compose.mainClock.advanceTimeBy(20_000)

        compose.onNodeWithTag("${BENEFIT_ITEM_TAG}uno").assertIsDisplayed()
    }

    @Test
    fun `mientras el usuario lo toca no avanza, y al soltar la cuenta arranca de nuevo`() {
        compose.mainClock.autoAdvance = false
        show(listOf(uno, dos, tres))

        compose.onNodeWithTag("${BENEFIT_ITEM_TAG}uno").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(12_000)
        assertPage(1, of = 3)

        compose.onNodeWithTag("${BENEFIT_ITEM_TAG}uno").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(4_000)
        assertPage(1, of = 3)

        compose.mainClock.advanceTimeBy(2_000)
        assertPage(2, of = 3)
    }

    @Test
    fun `arrastrar cambia de pagina a mano`() {
        compose.mainClock.autoAdvance = false
        show(listOf(uno, dos, tres))

        compose.onNodeWithTag(CAROUSEL_TAG).performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1_000)

        assertPage(2, of = 3)
    }

    @Test
    fun `tocar un banner abre el detalle de esa promocion`() {
        val opened = mutableListOf<String>()
        show(listOf(uno, dos), onOpen = { opened += it })

        compose.onNodeWithTag("${BENEFIT_ITEM_TAG}uno").performClick()

        assertEquals(listOf("uno"), opened)
    }

    @Test
    fun `cada banner se pide por el id de su promocion`() {
        val requested = mutableListOf<String>()
        show(listOf(uno, dos), loadBanner = { id -> requested += id; null })

        compose.waitForIdle()

        assertEquals(true, "uno" in requested)
    }

    @Test
    fun `la siguiente pagina da la vuelta y tolera una lista vacia`() {
        assertEquals(1, nextCarouselPage(0, 3))
        assertEquals(0, nextCarouselPage(2, 3))
        assertEquals(0, nextCarouselPage(0, 1))
        assertEquals(0, nextCarouselPage(5, 0))
    }
}
