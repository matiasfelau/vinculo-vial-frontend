package ar.felaufernandezbottaro.vinculovial.presentation.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ar.felaufernandezbottaro.vinculovial.domain.benefits.Benefit
import ar.felaufernandezbottaro.vinculovial.domain.benefits.benefit
import ar.felaufernandezbottaro.vinculovial.presentation.benefits.BENEFIT_ITEM_TAG
import ar.felaufernandezbottaro.vinculovial.presentation.benefits.CAROUSEL_TAG
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El Home con el carrusel de beneficios (US-18) debajo de "Mis alertas". */
@RunWith(RobolectricTestRunner::class)
// Pantalla alta: el contenido del detalle y del Home queda a la vista sin scrollear.
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class SettingsScreenBenefitsTest {

    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private var inboxOpened = 0

    private fun show(benefits: List<Benefit>) {
        compose.setContent {
            VinculoVialTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    onLogout = {},
                    onLinkVehicle = {},
                    onScanPlate = {},
                    onOpenInbox = { inboxOpened++ },
                    onDeleteAccount = {},
                    onBiometricToggle = {},
                    onBiometricOfferDismissed = {},
                    onBiometricActivationFailureDismissed = {},
                    benefits = benefits,
                    onOpenBenefit = { opened += it },
                )
            }
        }
    }

    @Test
    fun `con promociones el Home muestra el carrusel`() {
        show(listOf(benefit(id = "uno", title = "Estación de prueba")))

        compose.onNodeWithTag(CAROUSEL_TAG).assertIsDisplayed()
        compose.onNodeWithTag("${BENEFIT_ITEM_TAG}uno").assertIsDisplayed()
    }

    @Test
    fun `sin promociones el Home no muestra el carrusel`() {
        show(emptyList())

        compose.onNodeWithTag(CAROUSEL_TAG).assertDoesNotExist()
        compose.onNodeWithText("Mis alertas").assertIsDisplayed()
    }

    @Test
    fun `el carrusel esta debajo del boton Mis alertas`() {
        show(listOf(benefit(id = "uno")))

        val alertsBottom = compose.onNodeWithText("Mis alertas").getUnclippedBoundsInRoot().bottom
        val carouselTop = compose.onNodeWithTag(CAROUSEL_TAG).getUnclippedBoundsInRoot().top

        assertTrue("el carrusel (top=$carouselTop) debe estar debajo de Mis alertas (bottom=$alertsBottom)", carouselTop >= alertsBottom)
    }

    @Test
    fun `tocar un banner abre el detalle de esa promocion y no el buzon`() {
        show(listOf(benefit(id = "uno")))

        compose.onNodeWithTag("${BENEFIT_ITEM_TAG}uno").performClick()

        assertEquals(listOf("uno"), opened)
        assertEquals(0, inboxOpened)
    }

    @Test
    fun `Mis alertas sigue abriendo el buzon con el carrusel puesto`() {
        show(listOf(benefit(id = "uno")))

        compose.onNodeWithText("Mis alertas").performClick()

        assertEquals(1, inboxOpened)
    }
}
