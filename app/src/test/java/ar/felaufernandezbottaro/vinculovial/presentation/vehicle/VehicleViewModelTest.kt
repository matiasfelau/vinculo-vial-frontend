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
import org.junit.Assert.assertTrue
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

        assertEquals(VehicleStatus.Failed(LinkVehicleError.OwnerMismatch, cedula), viewModel.uiState.value.status)
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

    @Test
    fun `un reintento vuelve a enviar la cedula al repositorio`() = runTest {
        // Primera vez falla
        repository.result = LinkVehicleResult.Failed(LinkVehicleError.Network)
        val viewModel = viewModel()
        viewModel.onCedulaScanned(cedula)

        assertEquals(VehicleStatus.Failed(LinkVehicleError.Network, cedula), viewModel.uiState.value.status)
        assertEquals(1, repository.received.size)

        // Se arregla la red y se reintenta
        repository.result = LinkVehicleResult.Linked
        viewModel.onCedulaScanned(cedula) // Reusa la cedula

        assertEquals(VehicleStatus.Linked, viewModel.uiState.value.status)
        assertEquals(2, repository.received.size)
    }

    /**
     * Lo que hace el botón "Reintentar" de `ScanCedulaScreen`, en el mismo orden: descarta el
     * error (`onErrorDismissed`) y reenvía la cédula que quedó en `Failed`. El test anterior
     * llama a `onCedulaScanned` directo, sin pasar por `onErrorDismissed`: no ejercita el
     * camino real de la UI.
     */
    @Test
    fun `reintentar tras un error transitorio reenvia la misma cedula sin volver a escanear`() = runTest {
        repository.result = LinkVehicleResult.Failed(LinkVehicleError.Unavailable)
        val viewModel = viewModel()
        viewModel.onCedulaScanned(cedula)
        val failed = viewModel.uiState.value.status as VehicleStatus.Failed
        assertTrue(failed.error.isTransient)

        repository.result = LinkVehicleResult.Linked
        viewModel.onErrorDismissed()
        viewModel.onCedulaScanned(failed.cedula)

        assertEquals(VehicleStatus.Linked, viewModel.uiState.value.status)
        assertEquals(listOf(cedula, cedula), repository.received)
        assertEquals(0, scanner.starts) // el reintento nunca reinicia el escáner
    }

    @Test
    fun `un reintento que vuelve a fallar deja otra vez la cedula disponible`() = runTest {
        repository.result = LinkVehicleResult.Failed(LinkVehicleError.Network)
        val viewModel = viewModel()
        viewModel.onCedulaScanned(cedula)

        // Primer reintento: la cédula sale del estado Failed, no del escáner.
        val firstFailure = viewModel.uiState.value.status as VehicleStatus.Failed
        viewModel.onErrorDismissed()
        viewModel.onCedulaScanned(firstFailure.cedula)

        // Vuelve a fallar: el estado Failed trae la misma cédula para un tercer intento.
        val secondFailure = viewModel.uiState.value.status as VehicleStatus.Failed
        assertEquals(VehicleStatus.Failed(LinkVehicleError.Network, cedula), secondFailure)
        assertEquals(listOf(cedula, cedula), repository.received)
    }

    /** Cancelar o "Entendido" no debe conservar la cédula: `Idle` no la lleva. */
    @Test
    fun `descartar el error suelta la cedula`() = runTest {
        repository.result = LinkVehicleResult.Failed(LinkVehicleError.OwnerMismatch)
        val viewModel = viewModel()
        viewModel.onCedulaScanned(cedula)

        viewModel.onErrorDismissed()

        assertEquals(VehicleStatus.Idle, viewModel.uiState.value.status)
    }
}
