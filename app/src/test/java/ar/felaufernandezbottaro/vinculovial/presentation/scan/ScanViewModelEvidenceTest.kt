package ar.felaufernandezbottaro.vinculovial.presentation.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceImageProcessor
import ar.felaufernandezbottaro.vinculovial.data.scan.FrameCropper
import ar.felaufernandezbottaro.vinculovial.data.scan.PlateRegexFilter
import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidenceRole
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.FakeAlertAttemptRepository
import ar.felaufernandezbottaro.vinculovial.domain.scan.FakeAlertRepository
import ar.felaufernandezbottaro.vinculovial.domain.scan.FakePlateRecognitionStrategy
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertResult
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertUseCase
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptUseCase
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** La captura guiada de evidencia dentro de `ScanViewModel`: pasos, procesamiento y wipe. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelEvidenceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val attemptRepository = FakeAlertAttemptRepository()
    private val alertRepository = FakeAlertRepository()
    private val rasterFactory = StubEvidenceRasterFactory()

    private fun viewModel() = ScanViewModel(
        frameCropper = FrameCropper(),
        recognitionStrategy = FakePlateRecognitionStrategy(),
        regexFilter = PlateRegexFilter(),
        startAlertAttempt = StartAlertAttemptUseCase(attemptRepository),
        sendAlert = SendAlertUseCase(alertRepository),
        evidenceProcessor = EvidenceImageProcessor(rasterFactory, UnconfinedTestDispatcher()),
        galleryEvidenceSource = FakeGalleryEvidenceSource(),
    )

    /** Un `ViewModel` con intento abierto, listo para elegir el tipo de alerta. */
    private fun withAttempt(): ScanViewModel = viewModel().also {
        it.onPlateFound("GUQ856")
        it.onPlateConfirmed()
    }

    private fun ScanViewModel.capturing(alertType: AlertType) = apply { onAlertTypeSelected(alertType) }

    private val ScanViewModel.evidence: EvidenceCaptureState get() = uiState.value.evidence

    private fun ScanViewModel.takePhoto(rotationDegrees: Int = 0) =
        onPhotoCaptured(ByteArray(8) { 1 }, rotationDegrees)

    private fun ScanViewModel.completeEvidence() {
        while (evidence.phase != EvidencePhase.COMPLETE) {
            takePhoto()
            onStepConfirmed()
        }
    }

    // -- Elegir el tipo ---------------------------------------------------------------------

    @Test
    fun `elegir un tipo ofrecido arranca la captura en el primer paso`() = runTest {
        val viewModel = withAttempt()

        viewModel.onAlertTypeSelected(AlertType.CRASHED)

        assertEquals(EvidencePhase.CAPTURING, viewModel.evidence.phase)
        assertEquals(
            listOf(EvidenceRole.GENERAL, EvidenceRole.PLATE, EvidenceRole.DETAIL),
            viewModel.evidence.steps.map { it.role },
        )
        assertEquals(EvidenceRole.GENERAL, viewModel.evidence.currentStep?.role)
    }

    @Test
    fun `un tipo deshabilitado no se puede elegir`() = runTest {
        val viewModel = withAttempt()

        viewModel.onAlertTypeSelected(AlertType.PARKED_TOO_CLOSE)

        assertNull(viewModel.uiState.value.selectedAlertType)
        assertEquals(EvidencePhase.IDLE, viewModel.evidence.phase)
    }

    @Test
    fun `sin un intento abierto elegir un tipo no hace nada`() = runTest {
        val viewModel = viewModel()

        viewModel.onAlertTypeSelected(AlertType.CRASHED)

        assertNull(viewModel.uiState.value.selectedAlertType)
        assertEquals(EvidencePhase.IDLE, viewModel.evidence.phase)
    }

    @Test
    fun `volver a elegir un tipo descarta con ceros las fotos del anterior`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.takePhoto()

        viewModel.onAlertTypeSelected(AlertType.PARKED_ON_GARAGE)

        assertTrue(rasterFactory.allWiped())
        assertEquals(0, viewModel.evidence.stepIndex)
        assertEquals(EvidencePhase.CAPTURING, viewModel.evidence.phase)
    }

    // -- Procesar la foto ------------------------------------------------------------------

    @Test
    fun `una foto sacada pasa a revision y pasa por el procesador con su rotacion`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)

        viewModel.takePhoto(rotationDegrees = 90)

        assertEquals(EvidencePhase.REVIEWING, viewModel.evidence.phase)
        assertEquals(listOf(90), rasterFactory.rotationsReceived)
    }

    @Test
    fun `los bytes de entrada se sobreescriben con ceros`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        val jpeg = ByteArray(8) { 1 }

        viewModel.onPhotoCaptured(jpeg, 0)

        assertArrayEquals(ByteArray(8), jpeg)
    }

    @Test
    fun `una imagen ilegible vuelve a la camara con el error`() = runTest {
        rasterFactory.decodable = false
        val viewModel = withAttempt().capturing(AlertType.CRASHED)

        viewModel.takePhoto()

        assertEquals(EvidencePhase.CAPTURING, viewModel.evidence.phase)
        assertEquals(EvidenceError.UNREADABLE, viewModel.evidence.error)
    }

    @Test
    fun `una imagen que no entra en el tamaño maximo vuelve a la camara con el error`() = runTest {
        rasterFactory.encodedSize = 1_000_000
        val viewModel = withAttempt().capturing(AlertType.CRASHED)

        viewModel.takePhoto()

        assertEquals(EvidencePhase.CAPTURING, viewModel.evidence.phase)
        assertEquals(EvidenceError.TOO_LARGE, viewModel.evidence.error)
        assertTrue(rasterFactory.allWiped())
    }

    @Test
    fun `si la camara falla se queda en el mismo paso con el error`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)

        viewModel.onPhotoCaptureFailed()

        assertEquals(EvidencePhase.CAPTURING, viewModel.evidence.phase)
        assertEquals(EvidenceError.CAPTURE_FAILED, viewModel.evidence.error)
        assertEquals(0, viewModel.evidence.stepIndex)
    }

    @Test
    fun `una foto que llega fuera de la captura se descarta con ceros`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.takePhoto()
        val late = ByteArray(8) { 1 }

        viewModel.onPhotoCaptured(late, 0)

        assertArrayEquals(ByteArray(8), late)
        assertEquals(1, rasterFactory.encoded.size)
    }

    // -- Pasos: repetir, continuar, agregar otra --------------------------------------------

    @Test
    fun `repetir la foto sobreescribe con ceros la anterior y vuelve a la camara en el mismo paso`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.takePhoto()

        viewModel.onPhotoRetake()

        assertTrue(rasterFactory.allWiped())
        assertEquals(EvidencePhase.CAPTURING, viewModel.evidence.phase)
        assertEquals(0, viewModel.evidence.stepIndex)
    }

    @Test
    fun `continuar avanza al paso siguiente y el ultimo completa la captura`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.PARKED_ON_GARAGE)

        viewModel.takePhoto()
        viewModel.onStepConfirmed()
        assertEquals(EvidenceRole.PLATE, viewModel.evidence.currentStep?.role)

        viewModel.takePhoto()
        viewModel.onStepConfirmed()
        assertEquals(EvidenceRole.CONTEXT, viewModel.evidence.currentStep?.role)
        assertTrue(viewModel.evidence.isLastStep)

        viewModel.takePhoto()
        viewModel.onStepConfirmed()
        assertEquals(EvidencePhase.COMPLETE, viewModel.evidence.phase)
    }

    @Test
    fun `solo el daño admite agregar otra foto, hasta 3`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        repeat(2) {
            viewModel.takePhoto()
            viewModel.onStepConfirmed()
        }

        viewModel.takePhoto()
        assertTrue(viewModel.evidence.canAddAnother)
        viewModel.onAddAnotherPhoto()
        assertEquals(1, viewModel.evidence.photoIndex)

        viewModel.takePhoto()
        viewModel.onAddAnotherPhoto()
        assertEquals(2, viewModel.evidence.photoIndex)

        viewModel.takePhoto()
        assertFalse(viewModel.evidence.canAddAnother)
        viewModel.onAddAnotherPhoto()
        assertEquals(2, viewModel.evidence.photoIndex)
        assertEquals(EvidencePhase.REVIEWING, viewModel.evidence.phase)
    }

    @Test
    fun `los pasos de una sola foto no ofrecen agregar otra`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.takePhoto()

        assertFalse(viewModel.evidence.canAddAnother)
        viewModel.onAddAnotherPhoto()
        assertEquals(0, viewModel.evidence.photoIndex)
    }

    // -- Wipe: cancelacion, envio exitoso, onCleared ---------------------------------------

    @Test
    fun `descartar la captura sobreescribe con ceros todas las fotos y vuelve a elegir el tipo`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.takePhoto()
        viewModel.onStepConfirmed()
        viewModel.takePhoto()

        viewModel.onEvidenceDiscarded()

        assertTrue(rasterFactory.allWiped())
        assertNull(viewModel.uiState.value.selectedAlertType)
        assertEquals(EvidencePhase.IDLE, viewModel.evidence.phase)
    }

    @Test
    fun `cancelar el reporte sobreescribe con ceros todas las fotos`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()

        viewModel.onReportCancelled()

        assertTrue(rasterFactory.allWiped())
    }

    @Test
    fun `un envio exitoso sobreescribe con ceros todas las fotos`() = runTest {
        alertRepository.result = SendAlertResult.Sent
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()
        assertTrue(rasterFactory.anyAlive())

        viewModel.onReportRequested()

        assertTrue(rasterFactory.allWiped())
    }

    @Test
    fun `un envio fallido conserva las fotos para poder reintentar`() = runTest {
        alertRepository.result = SendAlertResult.Failed(SendAlertError.Unavailable)
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()

        viewModel.onReportRequested()

        assertTrue(rasterFactory.anyAlive())
    }

    @Test
    fun `onCleared sobreescribe con ceros todas las fotos`() = runTest {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel() as T
        }
        val viewModel = ViewModelProvider(store, factory)[ScanViewModel::class.java]
        viewModel.onPlateFound("GUQ856")
        viewModel.onPlateConfirmed()
        viewModel.onAlertTypeSelected(AlertType.CRASHED)
        viewModel.completeEvidence()
        assertTrue(rasterFactory.anyAlive())

        store.clear()

        assertTrue(rasterFactory.allWiped())
    }

    // -- Reportar ----------------------------------------------------------------------------

    @Test
    fun `reportar sin la evidencia completa no envia nada`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.takePhoto()

        viewModel.onReportRequested()

        assertEquals(emptyList<Any>(), alertRepository.received)
    }
}
