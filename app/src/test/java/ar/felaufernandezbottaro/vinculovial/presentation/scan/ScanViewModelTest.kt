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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * No ejercita `processFrame`: recorta/lee un `ImageProxy`/`Bitmap` real, que no existe en un
 * test JVM plano -- mismo límite que `FakeQrCedulaScanner` en `VehicleViewModelTest`. Estos
 * tests arrancan directamente en `ScanStatus.Detected` (como si `processFrame` ya hubiera
 * reconocido una patente) para cubrir la validación humana en el bucle y el fallback manual.
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

    // -- Confirmación humana (Detected -> Confirmado/Rechazado) --------------------------

    @Test
    fun `confirmar una patente detectada la manda al repositorio de check`() = runTest {
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)

        viewModel.onPlateConfirmed()

        assertEquals(listOf(patente), checkRepository.received)
    }

    @Test
    fun `sin una deteccion previa confirmar no hace nada`() = runTest {
        val viewModel = viewModel()

        viewModel.onPlateConfirmed()

        assertEquals(emptyList<String>(), checkRepository.received)
        assertEquals(ScanStatus.Scanning, viewModel.uiState.value.status)
    }

    @Test
    fun `rechazar una deteccion no llama al repositorio de check`() = runTest {
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)

        viewModel.onPlateRejected()

        assertEquals(emptyList<String>(), checkRepository.received)
        assertEquals(ScanStatus.Rejected, viewModel.uiState.value.status)
    }

    @Test
    fun `volver a escanear tras un rechazo vuelve a Scanning`() = runTest {
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)
        viewModel.onPlateRejected()

        viewModel.onRescan()

        assertEquals(ScanStatus.Scanning, viewModel.uiState.value.status)
    }

    // -- Ingreso manual --------------------------------------------------------------------

    @Test
    fun `abrir el ingreso manual desde un rechazo cambia el estado a ManualEntry`() = runTest {
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)
        viewModel.onPlateRejected()

        viewModel.onManualEntryOpened()

        assertEquals(ScanStatus.ManualEntry(), viewModel.uiState.value.status)
    }

    @Test
    fun `una patente tipeada valida entra al mismo embudo que una confirmada por camara`() = runTest {
        val viewModel = viewModel()

        viewModel.onManualPlateSubmitted("guq 856")

        assertEquals(listOf("GUQ856"), checkRepository.received)
    }

    @Test
    fun `una patente tipeada invalida no llama al repositorio y muestra un error`() = runTest {
        val viewModel = viewModel()

        viewModel.onManualPlateSubmitted("no es una patente")

        assertEquals(emptyList<String>(), checkRepository.received)
        val status = viewModel.uiState.value.status
        assertTrue(status is ScanStatus.ManualEntry)
        assertTrue((status as ScanStatus.ManualEntry).error != null)
    }

    @Test
    fun `un intento invalido mantiene el dialogo abierto para reintentar`() = runTest {
        val viewModel = viewModel()
        viewModel.onManualPlateSubmitted("invalida")

        viewModel.onManualPlateSubmitted(patente)

        assertEquals(listOf(patente), checkRepository.received)
    }

    @Test
    fun `cancelar el ingreso manual vuelve a Scanning sin llamar al repositorio`() = runTest {
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)
        viewModel.onPlateRejected()
        viewModel.onManualEntryOpened()

        viewModel.onManualEntryDismissed()

        assertEquals(ScanStatus.Scanning, viewModel.uiState.value.status)
        assertEquals(emptyList<String>(), checkRepository.received)
    }

    /**
     * El requerimiento central del fallback: el origen del dato (cámara vs. teclado) es
     * transparente para `CheckVehicleUseCase` -- la patente tipeada se cifra con HPKE
     * localmente exactamente igual que la escaneada, porque ambos caminos terminan en el
     * mismo caso de uso, sin distinguir cómo se obtuvo el `String`.
     */
    @Test
    fun `el origen del dato es transparente para el caso de uso`() = runTest {
        val porCamara = viewModel()
        porCamara.onPlateFound(patente)
        porCamara.onPlateConfirmed()

        val porTeclado = viewModel()
        porTeclado.onManualPlateSubmitted(patente)

        assertEquals(checkRepository.received, checkRepository.received)
        assertEquals(listOf(patente, patente), checkRepository.received)
    }

    // -- Resto del flujo de dos pasos, ya alcanzable sólo vía confirmación/ingreso manual --

    @Test
    fun `un 200 tras confirmar deja el estado en AwaitingAlertType`() = runTest {
        checkRepository.result = CheckVehicleResult.Found(patEnc = "pat-enc")
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)

        viewModel.onPlateConfirmed()

        assertEquals(ScanStatus.AwaitingAlertType, viewModel.uiState.value.status)
    }

    @Test
    fun `un 404 tras confirmar deja el estado en NotInNetwork`() = runTest {
        checkRepository.result = CheckVehicleResult.NotInNetwork
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)

        viewModel.onPlateConfirmed()

        assertEquals(ScanStatus.NotInNetwork, viewModel.uiState.value.status)
    }

    @Test
    fun `un error de check tras confirmar deja el estado en CheckFailed con el motivo`() = runTest {
        checkRepository.result = CheckVehicleResult.Failed(CheckVehicleError.RateLimited)
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)

        viewModel.onPlateConfirmed()

        assertEquals(ScanStatus.CheckFailed(CheckVehicleError.RateLimited), viewModel.uiState.value.status)
    }

    @Test
    fun `elegir un tipo de incidente reusa el pat_enc que devolvio el check`() = runTest {
        checkRepository.result = CheckVehicleResult.Found(patEnc = "pat-enc-del-check")
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)
        viewModel.onPlateConfirmed()

        viewModel.onAlertTypeSelected(AlertType.CRASHED)

        assertEquals(listOf(AlertType.CRASHED to "pat-enc-del-check"), alertRepository.received)
    }

    @Test
    fun `un 202 deja el estado en AlertSent`() = runTest {
        checkRepository.result = CheckVehicleResult.Found(patEnc = "pat-enc")
        alertRepository.result = SendAlertResult.Sent
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)
        viewModel.onPlateConfirmed()

        viewModel.onAlertTypeSelected(AlertType.CRASHED)

        assertEquals(ScanStatus.AlertSent, viewModel.uiState.value.status)
    }

    @Test
    fun `un error de alert deja el estado en AlertFailed con el motivo`() = runTest {
        checkRepository.result = CheckVehicleResult.Found(patEnc = "pat-enc")
        alertRepository.result = SendAlertResult.Failed(SendAlertError.InvalidData)
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)
        viewModel.onPlateConfirmed()

        viewModel.onAlertTypeSelected(AlertType.CRASHED)

        assertEquals(ScanStatus.AlertFailed(SendAlertError.InvalidData), viewModel.uiState.value.status)
    }

    @Test
    fun `descartar el catalogo sin elegir vuelve a Scanning`() = runTest {
        checkRepository.result = CheckVehicleResult.Found(patEnc = "pat-enc")
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)
        viewModel.onPlateConfirmed()

        viewModel.onAlertTypeDismissed()

        assertEquals(ScanStatus.Scanning, viewModel.uiState.value.status)
    }

    @Test
    fun `cerrar el mensaje de no esta en la red vuelve a Scanning`() = runTest {
        checkRepository.result = CheckVehicleResult.NotInNetwork
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)
        viewModel.onPlateConfirmed()

        viewModel.onNotInNetworkDismissed()

        assertEquals(ScanStatus.Scanning, viewModel.uiState.value.status)
    }

    @Test
    fun `cerrar un error vuelve a Scanning`() = runTest {
        checkRepository.result = CheckVehicleResult.Failed(CheckVehicleError.Network)
        val viewModel = viewModel()
        viewModel.onPlateFound(patente)
        viewModel.onPlateConfirmed()

        viewModel.onErrorDismissed()

        assertEquals(ScanStatus.Scanning, viewModel.uiState.value.status)
    }
}
