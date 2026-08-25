package ar.felaufernandezbottaro.vinculovial.presentation.scan

import ar.felaufernandezbottaro.vinculovial.data.scan.FrameCropper
import ar.felaufernandezbottaro.vinculovial.data.scan.PlateRegexFilter
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.CheckVehicleError
import ar.felaufernandezbottaro.vinculovial.domain.scan.CheckVehicleResult
import ar.felaufernandezbottaro.vinculovial.domain.scan.CheckVehicleUseCase
import ar.felaufernandezbottaro.vinculovial.domain.scan.FakeAlertRepository
import ar.felaufernandezbottaro.vinculovial.domain.scan.FakePlateRecognitionStrategy
import ar.felaufernandezbottaro.vinculovial.domain.scan.FakeVehicleCheckRepository
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertResult
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertUseCase
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * No ejercita `processFrame`: recorta/lee un `ImageProxy`/`Bitmap` real, que no existe en un
 * test JVM plano -- mismo límite que `FakeQrCedulaScanner` en `VehicleViewModelTest`. Estos
 * tests cubren el pipeline de negocio a partir de una patente ya reconocida.
 */
class ScanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val checkRepository = FakeVehicleCheckRepository()
    private val alertRepository = FakeAlertRepository()

    private fun viewModel() = ScanViewModel(
        frameCropper = FrameCropper(),
        recognitionStrategy = FakePlateRecognitionStrategy(),
        regexFilter = PlateRegexFilter(),
        checkVehicle = CheckVehicleUseCase(checkRepository),
        sendAlert = SendAlertUseCase(alertRepository),
    )

    private val patente = "GUQ856"

    @Test
    fun `una patente detectada se manda al repositorio de check`() = runTest {
        val viewModel = viewModel()

        viewModel.onPlateDetected(patente)

        assertEquals(listOf(patente), checkRepository.received)
    }

    @Test
    fun `un 200 deja el estado en AwaitingAlertType`() = runTest {
        checkRepository.result = CheckVehicleResult.Found(patEnc = "pat-enc")
        val viewModel = viewModel()

        viewModel.onPlateDetected(patente)

        assertEquals(ScanStatus.AwaitingAlertType, viewModel.uiState.value.status)
    }

    @Test
    fun `un 404 deja el estado en NotInNetwork`() = runTest {
        checkRepository.result = CheckVehicleResult.NotInNetwork
        val viewModel = viewModel()

        viewModel.onPlateDetected(patente)

        assertEquals(ScanStatus.NotInNetwork, viewModel.uiState.value.status)
    }

    @Test
    fun `un error de check deja el estado en CheckFailed con el motivo`() = runTest {
        checkRepository.result = CheckVehicleResult.Failed(CheckVehicleError.RateLimited)
        val viewModel = viewModel()

        viewModel.onPlateDetected(patente)

        assertEquals(ScanStatus.CheckFailed(CheckVehicleError.RateLimited), viewModel.uiState.value.status)
    }

    @Test
    fun `elegir un tipo de incidente reusa el pat_enc que devolvio el check`() = runTest {
        checkRepository.result = CheckVehicleResult.Found(patEnc = "pat-enc-del-check")
        val viewModel = viewModel()
        viewModel.onPlateDetected(patente)

        viewModel.onAlertTypeSelected(AlertType.CRASHED)

        assertEquals(listOf(AlertType.CRASHED to "pat-enc-del-check"), alertRepository.received)
    }

    @Test
    fun `un 202 deja el estado en AlertSent`() = runTest {
        checkRepository.result = CheckVehicleResult.Found(patEnc = "pat-enc")
        alertRepository.result = SendAlertResult.Sent
        val viewModel = viewModel()
        viewModel.onPlateDetected(patente)

        viewModel.onAlertTypeSelected(AlertType.CRASHED)

        assertEquals(ScanStatus.AlertSent, viewModel.uiState.value.status)
    }

    @Test
    fun `un error de alert deja el estado en AlertFailed con el motivo`() = runTest {
        checkRepository.result = CheckVehicleResult.Found(patEnc = "pat-enc")
        alertRepository.result = SendAlertResult.Failed(SendAlertError.InvalidData)
        val viewModel = viewModel()
        viewModel.onPlateDetected(patente)

        viewModel.onAlertTypeSelected(AlertType.CRASHED)

        assertEquals(ScanStatus.AlertFailed(SendAlertError.InvalidData), viewModel.uiState.value.status)
    }

    @Test
    fun `sin un check previo elegir un tipo de incidente no manda nada`() = runTest {
        val viewModel = viewModel()

        viewModel.onAlertTypeSelected(AlertType.CRASHED)

        assertEquals(emptyList<Any>(), alertRepository.received)
    }

    @Test
    fun `descartar el catalogo sin elegir vuelve a Scanning`() = runTest {
        checkRepository.result = CheckVehicleResult.Found(patEnc = "pat-enc")
        val viewModel = viewModel()
        viewModel.onPlateDetected(patente)

        viewModel.onAlertTypeDismissed()

        assertEquals(ScanStatus.Scanning, viewModel.uiState.value.status)
    }

    @Test
    fun `descartar el catalogo sin elegir no reusa el pat_enc en un intento posterior`() = runTest {
        checkRepository.result = CheckVehicleResult.Found(patEnc = "pat-enc-viejo")
        val viewModel = viewModel()
        viewModel.onPlateDetected(patente)
        viewModel.onAlertTypeDismissed()

        // Sin pasar de nuevo por onPlateDetected, no debería quedar un pat_enc pendiente.
        viewModel.onAlertTypeSelected(AlertType.CRASHED)

        assertEquals(emptyList<Any>(), alertRepository.received)
    }

    @Test
    fun `cerrar el mensaje de no esta en la red vuelve a Scanning`() = runTest {
        checkRepository.result = CheckVehicleResult.NotInNetwork
        val viewModel = viewModel()
        viewModel.onPlateDetected(patente)

        viewModel.onNotInNetworkDismissed()

        assertEquals(ScanStatus.Scanning, viewModel.uiState.value.status)
    }

    @Test
    fun `cerrar un error vuelve a Scanning`() = runTest {
        checkRepository.result = CheckVehicleResult.Failed(CheckVehicleError.Network)
        val viewModel = viewModel()
        viewModel.onPlateDetected(patente)

        viewModel.onErrorDismissed()

        assertEquals(ScanStatus.Scanning, viewModel.uiState.value.status)
    }
}
