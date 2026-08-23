package ar.felaufernandezbottaro.vinculovial.presentation.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.FakeQrCedulaScanner
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.FakeVehicleRepository
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleResult
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleUseCase
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaData
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VehicleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeVehicleRepository()
    private val scanner = FakeQrCedulaScanner()

    private fun viewModel() = VehicleViewModel(
        linkVehicle = LinkVehicleUseCase(repository),
        qrCedulaScanner = scanner,
    )

    private val cedula = CedulaData(chasis = "APJ56565", patente = "GUQ856", nroControl = "5092160")

    @Test
    fun `una cedula escaneada se manda al repositorio`() = runTest {
        val viewModel = viewModel()

        viewModel.onCedulaScanned(cedula)

        assertEquals(listOf(cedula), repository.received)
    }

    @Test
    fun `un 201 deja el estado en Linked`() = runTest {
        repository.result = LinkVehicleResult.Linked
        val viewModel = viewModel()

        viewModel.onCedulaScanned(cedula)

        assertEquals(VehicleStatus.Linked, viewModel.uiState.value.status)
    }

    @Test
    fun `un error deja el estado en Failed con el motivo`() = runTest {
        repository.result = LinkVehicleResult.Failed(LinkVehicleError.OwnerMismatch)
        val viewModel = viewModel()

        viewModel.onCedulaScanned(cedula)

        assertEquals(VehicleStatus.Failed(LinkVehicleError.OwnerMismatch), viewModel.uiState.value.status)
    }

    @Test
    fun `onErrorDismissed vuelve a Idle solo si habia un error`() = runTest {
        repository.result = LinkVehicleResult.Failed(LinkVehicleError.Network)
        val viewModel = viewModel()
        viewModel.onCedulaScanned(cedula)

        viewModel.onErrorDismissed()

        assertEquals(VehicleStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `onErrorDismissed no toca un estado que no es error`() = runTest {
        repository.result = LinkVehicleResult.Linked
        val viewModel = viewModel()
        viewModel.onCedulaScanned(cedula)

        viewModel.onErrorDismissed()

        assertEquals(VehicleStatus.Linked, viewModel.uiState.value.status)
    }
}
