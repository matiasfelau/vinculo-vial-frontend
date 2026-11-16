package ar.felaufernandezbottaro.vinculovial.presentation.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.FakeVehicleRepository
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.GetVehicleCountUseCase
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleUseCase
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.UnlinkAllVehiclesUseCase
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.UnlinkVehicleUseCase
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleRepository
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleResult
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.VehicleCount
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VehiclesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeVehicleRepository()

    private fun viewModel(repository: VehicleRepository = this.repository) = VehiclesViewModel(
        getCount = GetVehicleCountUseCase(repository),
        link = LinkVehicleUseCase(repository),
        unlink = UnlinkVehicleUseCase(repository),
        unlinkAll = UnlinkAllVehiclesUseCase(repository),
    )

    private fun VehiclesViewModel.plateDialog() = uiState.value.dialog as VehicleDialog.Plate

    private fun VehiclesViewModel.escribirYConfirmar(plate: String) {
        onPlateChange(plate)
        onPlateContinue()
        onPlateConfirmed()
    }

    // --- Carga -------------------------------------------------------------------------------

    @Test
    fun `al abrir trae el conteo`() {
        repository.countResult = VehicleResult.Success(VehicleCount(count = 1, max = 3))

        val viewModel = viewModel()

        with(viewModel.uiState.value) {
            assertEquals(VehicleCount(1, 3), count)
            assertFalse(loading)
            assertNull(loadError)
        }
    }

    @Test
    fun `si el conteo falla muestra el error y se puede reintentar`() {
        repository.countResult = VehicleResult.Failed(VehicleError.Network)
        val viewModel = viewModel()
        assertEquals(VehicleError.Network, viewModel.uiState.value.loadError)
        assertNull(viewModel.uiState.value.count)

        repository.countResult = VehicleResult.Success(VehicleCount(2, 3))
        viewModel.load()

        assertEquals(VehicleCount(2, 3), viewModel.uiState.value.count)
        assertNull(viewModel.uiState.value.loadError)
    }

    @Test
    fun `sin conteo no se puede vincular`() {
        repository.countResult = VehicleResult.Failed(VehicleError.Unavailable)
        val viewModel = viewModel()

        viewModel.onLinkClicked()

        assertFalse(viewModel.uiState.value.canLink)
        assertEquals(VehicleDialog.None, viewModel.uiState.value.dialog)
    }

    // --- Tope --------------------------------------------------------------------------------

    @Test
    fun `con lugar se puede vincular y abre el modal`() {
        val viewModel = viewModel()

        viewModel.onLinkClicked()

        assertTrue(viewModel.uiState.value.canLink)
        assertEquals(PlateAction.LINK, viewModel.plateDialog().action)
    }

    @Test
    fun `al llegar al maximo el alta queda deshabilitada y no abre el modal`() {
        repository.countResult = VehicleResult.Success(VehicleCount(count = 3, max = 3))
        val viewModel = viewModel()

        viewModel.onLinkClicked()

        assertFalse(viewModel.uiState.value.canLink)
        assertEquals(VehicleDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun `el tope sale del servidor, no esta fijo en la app`() {
        repository.countResult = VehicleResult.Success(VehicleCount(count = 2, max = 2))

        assertFalse(viewModel().uiState.value.canLink)
    }

    // --- Modal de patente ----------------------------------------------------------------------

    @Test
    fun `el campo queda en mayusculas, sin espacios ni guiones y hasta 7`() {
        val viewModel = viewModel()
        viewModel.onLinkClicked()

        viewModel.onPlateChange("ab-12 3cdxyz")

        assertEquals("AB123CD", viewModel.plateDialog().input)
    }

    @Test
    fun `una patente vacia o con formato invalido no pasa a la confirmacion`() {
        val viewModel = viewModel()
        viewModel.onLinkClicked()

        viewModel.onPlateContinue()
        assertEquals(PlateProblem.REQUIRED, viewModel.plateDialog().problem)
        assertEquals(PlateStep.ENTRY, viewModel.plateDialog().step)

        viewModel.onPlateChange("AB12")
        viewModel.onPlateContinue()
        assertEquals(PlateProblem.FORMAT, viewModel.plateDialog().problem)
        assertEquals(PlateStep.ENTRY, viewModel.plateDialog().step)
    }

    @Test
    fun `escribir limpia el problema`() {
        val viewModel = viewModel()
        viewModel.onLinkClicked()
        viewModel.onPlateContinue()

        viewModel.onPlateChange("A")

        assertNull(viewModel.plateDialog().problem)
    }

    @Test
    fun `una patente valida pasa a la confirmacion y no manda nada todavia`() {
        val viewModel = viewModel()
        viewModel.onLinkClicked()
        viewModel.onPlateChange("AB123CD")

        viewModel.onPlateContinue()

        assertEquals(PlateStep.CONFIRM, viewModel.plateDialog().step)
        assertTrue("no debió mandar nada antes de confirmar", repository.linked.isEmpty())
    }

    @Test
    fun `confirmar sin pasar por la confirmacion no manda nada`() {
        val viewModel = viewModel()
        viewModel.onLinkClicked()
        viewModel.onPlateChange("AB123CD")

        viewModel.onPlateConfirmed()

        assertTrue(repository.linked.isEmpty())
    }

    @Test
    fun `volver de la confirmacion permite corregir la patente`() {
        val viewModel = viewModel()
        viewModel.onLinkClicked()
        viewModel.onPlateChange("AB123CD")
        viewModel.onPlateContinue()

        viewModel.onPlateBack()

        assertEquals(PlateStep.ENTRY, viewModel.plateDialog().step)
        assertEquals("AB123CD", viewModel.plateDialog().input)
    }

    @Test
    fun `vincular confirmado manda la patente, actualiza el conteo y cierra el modal`() {
        val viewModel = viewModel()
        viewModel.onLinkClicked()

        viewModel.escribirYConfirmar("ab123cd")

        assertEquals(listOf("AB123CD"), repository.linked)
        with(viewModel.uiState.value) {
            assertEquals(VehicleCount(1, 3), count)
            assertEquals(VehicleDialog.None, dialog)
            assertFalse(busy)
        }
    }

    @Test
    fun `desvincular una patente confirmada manda la baja y baja el contador`() {
        repository.countResult = VehicleResult.Success(VehicleCount(2, 3))
        val viewModel = viewModel()
        viewModel.onUnlinkClicked()
        assertEquals(PlateAction.UNLINK, viewModel.plateDialog().action)

        viewModel.escribirYConfirmar("AB123CD")

        assertEquals(listOf("AB123CD"), repository.unlinked)
        assertTrue(repository.linked.isEmpty())
        assertEquals(VehicleDialog.None, viewModel.uiState.value.dialog)
        assertEquals(0, viewModel.uiState.value.count?.count)
    }

    // --- Cada code de error ---------------------------------------------------------------------

    @Test
    fun `cada rechazo vuelve a la patente con su motivo a la vista`() {
        val casos = mapOf(
            VehicleError.Duplicate to "Ya tenés esta patente vinculada",
            VehicleError.LimitReached to "Podés tener hasta 3 vehículos",
            VehicleError.InvalidPlate to "Revisá el formato de la patente",
        )

        casos.forEach { (error, texto) ->
            val repo = FakeVehicleRepository(linkFailure = error)
            val viewModel = viewModel(repo)
            viewModel.onLinkClicked()

            viewModel.escribirYConfirmar("AB123CD")

            val dialog = viewModel.plateDialog()
            assertEquals("$error", error, dialog.error)
            assertEquals("$error vuelve a la patente", PlateStep.ENTRY, dialog.step)
            assertEquals(texto, dialog.error?.message(max = 3))
            assertFalse(viewModel.uiState.value.busy)
        }
    }

    @Test
    fun `desvincular una patente que no se tiene muestra que no esta vinculada`() {
        val repo = FakeVehicleRepository(unlinkFailure = VehicleError.NotFound)
        val viewModel = viewModel(repo)
        viewModel.onUnlinkClicked()

        viewModel.escribirYConfirmar("AB123CD")

        assertEquals(VehicleError.NotFound, viewModel.plateDialog().error)
        assertEquals("No tenés esa patente vinculada", viewModel.plateDialog().error?.message(3))
    }

    @Test
    fun `los errores de red y de servidor tambien vuelven a la patente`() {
        listOf(VehicleError.Network, VehicleError.Unavailable, VehicleError.Unexpected, VehicleError.Configuration)
            .forEach { error ->
                val viewModel = viewModel(FakeVehicleRepository(linkFailure = error))
                viewModel.onLinkClicked()

                viewModel.escribirYConfirmar("AB123CD")

                assertEquals(error, viewModel.plateDialog().error)
            }
    }

    @Test
    fun `si el servidor dice que se llego al tope se actualiza el conteo`() {
        val repo = FakeVehicleRepository(linkFailure = VehicleError.LimitReached)
        val viewModel = viewModel(repo)
        repo.countResult = VehicleResult.Success(VehicleCount(2, 3))
        viewModel.load()
        viewModel.onLinkClicked()
        assertTrue(viewModel.uiState.value.canLink)

        // Mientras tanto otro dispositivo vinculó el tercero.
        repo.countResult = VehicleResult.Success(VehicleCount(3, 3))
        viewModel.escribirYConfirmar("AB123CD")

        assertEquals(VehicleCount(3, 3), viewModel.uiState.value.count)
        assertFalse(viewModel.uiState.value.canLink)
    }

    @Test
    fun `editar la patente despues de un rechazo limpia el error`() {
        val viewModel = viewModel(FakeVehicleRepository(linkFailure = VehicleError.Duplicate))
        viewModel.onLinkClicked()
        viewModel.escribirYConfirmar("AB123CD")

        viewModel.onPlateChange("AB123C")

        assertNull(viewModel.plateDialog().error)
    }

    // --- Desvincular todos ------------------------------------------------------------------------

    @Test
    fun `desvincular todos pide confirmacion antes de mandar nada`() {
        val viewModel = viewModel()

        viewModel.onUnlinkAllClicked()

        assertTrue(viewModel.uiState.value.dialog is VehicleDialog.UnlinkAll)
        assertEquals(0, repository.unlinkAllCalls)
    }

    @Test
    fun `confirmar desvincular todos deja el contador en cero y permite volver a vincular`() {
        repository.countResult = VehicleResult.Success(VehicleCount(3, 3))
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.canLink)
        viewModel.onUnlinkAllClicked()

        viewModel.onUnlinkAllConfirmed()

        assertEquals(1, repository.unlinkAllCalls)
        with(viewModel.uiState.value) {
            assertEquals(VehicleCount(0, 3), count)
            assertEquals(VehicleDialog.None, dialog)
            assertTrue(canLink)
        }
    }

    @Test
    fun `si desvincular todos falla el modal sigue abierto con el error`() {
        repository.unlinkAllResult = VehicleResult.Failed(VehicleError.Network)
        val viewModel = viewModel()
        viewModel.onUnlinkAllClicked()

        viewModel.onUnlinkAllConfirmed()

        assertEquals(VehicleDialog.UnlinkAll(VehicleError.Network), viewModel.uiState.value.dialog)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun `confirmar desvincular todos sin el modal abierto no hace nada`() {
        val viewModel = viewModel()

        viewModel.onUnlinkAllConfirmed()

        assertEquals(0, repository.unlinkAllCalls)
    }

    // --- Cerrar y doble toque ------------------------------------------------------------------------

    @Test
    fun `cancelar cierra el modal y descarta la patente`() {
        val viewModel = viewModel()
        viewModel.onLinkClicked()
        viewModel.onPlateChange("AB123CD")

        viewModel.onDialogDismissed()

        assertEquals(VehicleDialog.None, viewModel.uiState.value.dialog)
        viewModel.onLinkClicked()
        assertEquals("", viewModel.plateDialog().input)
    }

    @Test
    fun `un doble toque en confirmar manda una sola vez`() = runTest {
        val gate = CompletableDeferred<VehicleResult<VehicleCount>>()
        var calls = 0
        val gated = object : VehicleRepository {
            override suspend fun getCount() = VehicleResult.Success(VehicleCount(0, 3))

            override suspend fun link(plate: String): VehicleResult<VehicleCount> {
                calls++
                return gate.await()
            }

            override suspend fun unlink(plate: String): VehicleResult<VehicleCount> = gate.await()

            override suspend fun unlinkAll(): VehicleResult<Unit> = VehicleResult.Success(Unit)
        }
        val viewModel = viewModel(gated)
        viewModel.onLinkClicked()
        viewModel.onPlateChange("AB123CD")
        viewModel.onPlateContinue()

        viewModel.onPlateConfirmed()
        assertTrue(viewModel.uiState.value.busy)
        viewModel.onPlateConfirmed()
        viewModel.onDialogDismissed() // mientras se envía no se puede cerrar
        assertTrue(viewModel.uiState.value.dialog is VehicleDialog.Plate)
        gate.complete(VehicleResult.Success(VehicleCount(1, 3)))

        assertEquals(1, calls)
    }

    @Test
    fun `mientras hay una operacion en vuelo no se abren otros modales`() = runTest {
        val gate = CompletableDeferred<VehicleResult<Unit>>()
        val gated = object : VehicleRepository {
            override suspend fun getCount() = VehicleResult.Success(VehicleCount(1, 3))
            override suspend fun link(plate: String): VehicleResult<VehicleCount> = throw UnsupportedOperationException()
            override suspend fun unlink(plate: String): VehicleResult<VehicleCount> = throw UnsupportedOperationException()
            override suspend fun unlinkAll(): VehicleResult<Unit> = gate.await()
        }
        val viewModel = viewModel(gated)
        viewModel.onUnlinkAllClicked()
        viewModel.onUnlinkAllConfirmed()

        viewModel.onLinkClicked()
        viewModel.onUnlinkClicked()
        viewModel.onUnlinkAllClicked()
        assertTrue(viewModel.uiState.value.dialog is VehicleDialog.UnlinkAll)
        gate.complete(VehicleResult.Success(Unit))
    }

    // --- Privacidad ---------------------------------------------------------------------------------------

    @Test
    fun `el estado nunca se renderiza con la patente que se esta escribiendo`() {
        val viewModel = viewModel()
        viewModel.onLinkClicked()
        viewModel.onPlateChange("AB123CD")

        assertFalse(viewModel.uiState.value.toString().contains("AB123CD"))
        assertFalse(viewModel.uiState.value.dialog.toString().contains("AB123CD"))
    }
}
