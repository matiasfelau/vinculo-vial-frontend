package ar.felaufernandezbottaro.vinculovial.presentation.vehicle

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.VehicleCount
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La pantalla "Mis vehículos" (US-14), con Robolectric y `createComposeRule`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyVehiclesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: VehiclesUiState) {
        composeRule.setContent {
            VinculoVialTheme {
                MyVehiclesScreen(
                    state = state,
                    onBack = { events += "back" },
                    onLink = { events += "link" },
                    onUnlink = { events += "unlink" },
                    onUnlinkAll = { events += "unlinkAll" },
                    onRetry = { events += "retry" },
                    onPlateChange = { events += "plate:$it" },
                    onPlateContinue = { events += "continue" },
                    onPlateBack = { events += "plateBack" },
                    onPlateConfirm = { events += "confirm" },
                    onUnlinkAllConfirm = { events += "unlinkAllConfirm" },
                    onDialogDismiss = { events += "dismiss" },
                )
            }
        }
    }

    private fun loaded(count: Int, dialog: VehicleDialog = VehicleDialog.None, busy: Boolean = false) =
        VehiclesUiState(count = VehicleCount(count, 3), loading = false, dialog = dialog, busy = busy)

    // --- Pantalla principal ------------------------------------------------------------------------

    @Test
    fun `muestra cuantos vehiculos hay vinculados y nunca las patentes`() {
        show(loaded(count = 1))

        composeRule.onNodeWithText("Tenés 1 de 3 vehículos vinculados").assertExists()
        composeRule.onNodeWithText("Vincular vehículo").assertIsEnabled()
        composeRule.onNodeWithText("Desvincular un vehículo").assertIsEnabled()
        composeRule.onNodeWithText("Desvincular todos").assertIsEnabled()
        composeRule.onNodeWithText("Podés tener hasta 3 vehículos").assertDoesNotExist()
    }

    @Test
    fun `al llegar al maximo el alta queda deshabilitada con el texto del tope`() {
        show(loaded(count = 3))

        composeRule.onNodeWithText("Tenés 3 de 3 vehículos vinculados").assertExists()
        composeRule.onNodeWithText("Vincular vehículo").assertIsNotEnabled()
        composeRule.onNodeWithText("Podés tener hasta 3 vehículos").assertExists()
        composeRule.onNodeWithText("Desvincular un vehículo").assertIsEnabled()
    }

    @Test
    fun `cada boton emite su evento`() {
        show(loaded(count = 1))

        composeRule.onNodeWithText("Vincular vehículo").performClick()
        composeRule.onNodeWithText("Desvincular un vehículo").performClick()
        composeRule.onNodeWithText("Desvincular todos").performClick()
        composeRule.onNodeWithContentDescription("Volver").performClick()

        assertEquals(listOf("link", "unlink", "unlinkAll", "back"), events)
    }

    @Test
    fun `mientras hay una operacion en vuelo los botones quedan deshabilitados`() {
        show(loaded(count = 1, busy = true))

        composeRule.onNodeWithText("Vincular vehículo").assertIsNotEnabled()
        composeRule.onNodeWithText("Desvincular un vehículo").assertIsNotEnabled()
        composeRule.onNodeWithText("Desvincular todos").assertIsNotEnabled()
    }

    @Test
    fun `si no se pudo contar muestra el error y reintentar`() {
        show(VehiclesUiState(loading = false, loadError = VehicleError.Network))

        composeRule.onNodeWithText("Tenés", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("No pudimos comunicarnos con el servidor", substring = true).assertExists()
        composeRule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf("retry"), events)
    }

    @Test
    fun `mientras carga no muestra botones`() {
        show(VehiclesUiState(loading = true))

        composeRule.onNodeWithText("Vincular vehículo").assertDoesNotExist()
        composeRule.onNodeWithText("Mis vehículos").assertExists()
    }

    // --- Modal de patente ------------------------------------------------------------------------------

    @Test
    fun `el modal de alta pide la patente y emite lo que se escribe`() {
        show(loaded(1, VehicleDialog.Plate(PlateAction.LINK)))

        composeRule.onNodeWithText("PATENTE").assertExists()
        composeRule.onNodeWithText("AB123CD").performTextInput("ab123cd")
        composeRule.onNodeWithText("Continuar").performClick()
        composeRule.onNodeWithText("Cancelar").performClick()

        assertEquals(listOf("plate:ab123cd", "continue", "dismiss"), events)
    }

    @Test
    fun `el modal de alta muestra el problema de formato`() {
        show(loaded(1, VehicleDialog.Plate(PlateAction.LINK, input = "AB12", problem = PlateProblem.FORMAT)))

        composeRule.onNodeWithText("La patente tiene que ser AAA999 o AA999AA.").assertExists()
    }

    private fun showRejection(error: VehicleError) =
        show(loaded(1, VehicleDialog.Plate(PlateAction.UNLINK, input = "AB123CD", error = error)))

    @Test
    fun `DUPLICATE_VEHICLE muestra que ya esta vinculada`() {
        showRejection(VehicleError.Duplicate)

        composeRule.onNodeWithText("Ya tenés esta patente vinculada").assertExists()
    }

    @Test
    fun `VEHICLE_LIMIT_REACHED muestra el mismo texto del tope`() {
        showRejection(VehicleError.LimitReached)

        composeRule.onNodeWithText("Podés tener hasta 3 vehículos").assertExists()
    }

    @Test
    fun `INVALID_PLATE muestra que se revise el formato`() {
        showRejection(VehicleError.InvalidPlate)

        composeRule.onNodeWithText("Revisá el formato de la patente").assertExists()
    }

    @Test
    fun `VEHICLE_NOT_FOUND muestra que no esta vinculada`() {
        showRejection(VehicleError.NotFound)

        composeRule.onNodeWithText("No tenés esa patente vinculada").assertExists()
    }

    @Test
    fun `la confirmacion de alta dice que patente se vincula y que se reciben sus alertas`() {
        show(loaded(1, VehicleDialog.Plate(PlateAction.LINK, input = "AB123CD", step = PlateStep.CONFIRM)))

        composeRule
            .onNodeWithText("Vas a vincular AB123CD. Vas a recibir las alertas de esta patente")
            .assertExists()
        composeRule.onNodeWithText("Confirmar").performClick()
        composeRule.onNodeWithText("Volver").performClick()

        assertEquals(listOf("confirm", "plateBack"), events)
    }

    @Test
    fun `la confirmacion de baja dice que patente se desvincula`() {
        show(loaded(1, VehicleDialog.Plate(PlateAction.UNLINK, input = "AB123CD", step = PlateStep.CONFIRM)))

        composeRule
            .onNodeWithText("Vas a desvincular AB123CD. Vas a dejar de recibir las alertas de esta patente")
            .assertExists()
    }

    @Test
    fun `mientras se envia el modal no se puede confirmar ni cancelar`() {
        show(loaded(1, VehicleDialog.Plate(PlateAction.LINK, input = "AB123CD", step = PlateStep.CONFIRM), busy = true))

        composeRule.onNodeWithText("Confirmar").assertIsNotEnabled()
        composeRule.onNodeWithText("Volver").assertIsNotEnabled()
    }

    // --- Desvincular todos ------------------------------------------------------------------------------

    @Test
    fun `desvincular todos pide confirmacion con el texto del contrato`() {
        show(loaded(2, VehicleDialog.UnlinkAll()))

        composeRule
            .onNodeWithText("Vas a dejar de recibir las alertas de todos tus vehículos")
            .assertExists()
        composeRule.onNodeWithText("Confirmar").performClick()
        composeRule.onNodeWithText("Cancelar").performClick()

        assertEquals(listOf("unlinkAllConfirm", "dismiss"), events)
    }

    @Test
    fun `si desvincular todos falla se ve el motivo en el modal`() {
        show(loaded(2, VehicleDialog.UnlinkAll(error = VehicleError.Network)))

        composeRule.onNodeWithText("No pudimos comunicarnos con el servidor", substring = true).assertExists()
    }
}
