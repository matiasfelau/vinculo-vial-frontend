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
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptResult
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptUseCase
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** La captura guiada de evidencia dentro de `ScanViewModel`: pasos, procesamiento y wipe. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelEvidenceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val attemptRepository = FakeAlertAttemptRepository()
    private val alertRepository = FakeAlertRepository()
    private val rasterFactory = StubEvidenceRasterFactory()
    private val clock = Clock.fixed(Instant.parse("2029-12-31T00:00:00Z"), ZoneOffset.UTC)

    private fun viewModel() = ScanViewModel(
        frameCropper = FrameCropper(),
        recognitionStrategy = FakePlateRecognitionStrategy(),
        regexFilter = PlateRegexFilter(),
        startAlertAttempt = StartAlertAttemptUseCase(attemptRepository),
        sendAlert = SendAlertUseCase(alertRepository, attemptRepository, clock),
        evidenceProcessor = EvidenceImageProcessor(rasterFactory, UnconfinedTestDispatcher()),
        galleryEvidenceSource = FakeGalleryEvidenceSource(),
    )

    /** Un `ViewModel` con intento abierto, listo para elegir el tipo de alerta. */
    private fun withAttempt(): ScanViewModel = viewModel().also {
        it.onPlateFound("GUQ856")
        it.onPlateConfirmed()
    }

    /** Elige el mensaje y toca "Tomar fotos": queda en la cámara, listo para sacar la primera foto. */
    private fun ScanViewModel.capturing(alertType: AlertType) = apply {
        onAlertTypeSelected(alertType)
        onCaptureStarted()
    }

    private val ScanViewModel.evidence: EvidenceCaptureState get() = uiState.value.evidence

    private fun ScanViewModel.takePhoto(rotationDegrees: Int = 0) =
        onPhotoCaptured(ByteArray(8) { 1 }, rotationDegrees)

    private fun ScanViewModel.completeEvidence() {
        if (evidence.phase == EvidencePhase.READY) onCaptureStarted()
        while (evidence.phase != EvidencePhase.COMPLETE) {
            takePhoto()
            onStepConfirmed()
        }
    }

    // -- Elegir el tipo ---------------------------------------------------------------------

    @Test
    fun `elegir un tipo ofrecido deja la captura lista pero no abre la camara`() = runTest {
        val viewModel = withAttempt()

        viewModel.onAlertTypeSelected(AlertType.CRASHED)

        assertEquals(AlertType.CRASHED, viewModel.uiState.value.selectedAlertType)
        assertEquals(EvidencePhase.READY, viewModel.evidence.phase)
        assertEquals(
            listOf(EvidenceRole.GENERAL, EvidenceRole.PLATE, EvidenceRole.DETAIL),
            viewModel.evidence.steps.map { it.role },
        )
        assertEquals(EvidenceRole.GENERAL, viewModel.evidence.currentStep?.role)
    }

    @Test
    fun `mientras el usuario lee no se acepta ninguna foto`() = runTest {
        val viewModel = withAttempt()
        viewModel.onAlertTypeSelected(AlertType.CRASHED)
        val early = ByteArray(8) { 1 }

        viewModel.onPhotoCaptured(early, 0)

        assertArrayEquals(ByteArray(8), early)
        assertEquals(EvidencePhase.READY, viewModel.evidence.phase)
        assertEquals(emptyList<Int>(), rasterFactory.rotationsReceived)
    }

    // -- "Tomar fotos" y volver de la cámara --------------------------------------------------

    @Test
    fun `Tomar fotos abre la captura en el primer paso`() = runTest {
        val viewModel = withAttempt()
        viewModel.onAlertTypeSelected(AlertType.CRASHED)

        viewModel.onCaptureStarted()

        assertEquals(EvidencePhase.CAPTURING, viewModel.evidence.phase)
        assertEquals(0, viewModel.evidence.stepIndex)
        assertEquals(0, viewModel.evidence.photoIndex)
    }

    @Test
    fun `Tomar fotos sin haber elegido un mensaje no hace nada`() = runTest {
        val viewModel = withAttempt()

        viewModel.onCaptureStarted()

        assertEquals(EvidencePhase.IDLE, viewModel.evidence.phase)
    }

    @Test
    fun `tocar Tomar fotos de nuevo en plena captura no la reinicia`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.takePhoto()

        viewModel.onCaptureStarted()

        assertEquals(EvidencePhase.REVIEWING, viewModel.evidence.phase)
        assertTrue(rasterFactory.anyAlive())
    }

    @Test
    fun `volver atras desde la camara regresa al chat con el mensaje elegido y las fotos descartadas`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.takePhoto()
        viewModel.onStepConfirmed()
        viewModel.takePhoto()

        viewModel.onCaptureCancelled()

        assertEquals(EvidencePhase.READY, viewModel.evidence.phase)
        assertEquals(AlertType.CRASHED, viewModel.uiState.value.selectedAlertType)
        assertEquals(0, viewModel.evidence.stepIndex)
        assertEquals(3, viewModel.evidence.steps.size)
        assertTrue(rasterFactory.allWiped())
    }

    @Test
    fun `volver atras sin haber sacado fotos tambien regresa a Tomar fotos`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.PARKED_ON_GARAGE)

        viewModel.onCaptureCancelled()

        assertEquals(EvidencePhase.READY, viewModel.evidence.phase)
        assertEquals(AlertType.PARKED_ON_GARAGE, viewModel.uiState.value.selectedAlertType)
    }

    @Test
    fun `despues de volver se puede tomar las fotos de nuevo desde el primer paso`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.takePhoto()
        viewModel.onStepConfirmed()
        viewModel.onCaptureCancelled()

        viewModel.onCaptureStarted()

        assertEquals(EvidencePhase.CAPTURING, viewModel.evidence.phase)
        assertEquals(0, viewModel.evidence.stepIndex)
        viewModel.completeEvidence()
        assertEquals(EvidencePhase.COMPLETE, viewModel.evidence.phase)
    }

    @Test
    fun `con las fotos completas volver atras no las descarta`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()

        viewModel.onCaptureCancelled()

        assertEquals(EvidencePhase.COMPLETE, viewModel.evidence.phase)
        assertTrue(rasterFactory.anyAlive())
    }

    @Test
    fun `cancelar el reporte despues de volver de la camara descarta tambien la eleccion`() = runTest {
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.onCaptureCancelled()

        viewModel.onReportCancelled()

        assertNull(viewModel.uiState.value.selectedAlertType)
        assertEquals(EvidencePhase.IDLE, viewModel.evidence.phase)
    }

    @Test
    fun `solo se puede enviar con las fotos completas, no mientras el usuario lee`() = runTest {
        val viewModel = withAttempt()
        viewModel.onAlertTypeSelected(AlertType.CRASHED)

        viewModel.onReportRequested()

        assertEquals(emptyList<Any>(), alertRepository.received)
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
        assertEquals(EvidencePhase.READY, viewModel.evidence.phase)
        assertEquals(AlertType.PARKED_ON_GARAGE, viewModel.uiState.value.selectedAlertType)
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

    // -- Envio: intento vencido, reintento, reinicio ------------------------------------------

    private val expired = SendAlertResult.Failed(SendAlertError.AttemptExpired)

    @Test
    fun `un 410 renueva el intento y tras un reintento manual se usa el intento renovado`() = runTest {
        alertRepository.queue += expired
        alertRepository.queue += SendAlertResult.Failed(SendAlertError.Unavailable)
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()

        viewModel.onReportRequested()
        assertEquals(ScanStatus.AlertFailed(SendAlertError.Unavailable), viewModel.uiState.value.status)
        assertEquals(listOf("attempt-de-prueba", "attempt-renovado"), alertRepository.received.map { it.attemptId })

        viewModel.onAlertRetry()

        assertEquals("attempt-renovado", alertRepository.received.last().attemptId)
        assertEquals(ScanStatus.AlertSent, viewModel.uiState.value.status)
        assertEquals(1, attemptRepository.renewed.size)
    }

    @Test
    fun `un reintento manual conserva las fotos hasta que el envio sale bien`() = runTest {
        alertRepository.queue += SendAlertResult.Failed(SendAlertError.Unavailable)
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()

        viewModel.onReportRequested()
        assertTrue(rasterFactory.anyAlive())

        viewModel.onAlertRetry()
        assertTrue(rasterFactory.allWiped())
    }

    @Test
    fun `404 ATTEMPT_NOT_FOUND descarta las fotos y reiniciar vuelve a escanear`() = runTest {
        alertRepository.result = SendAlertResult.Failed(SendAlertError.AttemptNotFound)
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()

        viewModel.onReportRequested()

        assertEquals(ScanStatus.AlertFailed(SendAlertError.AttemptNotFound), viewModel.uiState.value.status)
        assertTrue(rasterFactory.allWiped())

        viewModel.onFlowRestarted()

        assertEquals(ScanStatus.Scanning, viewModel.uiState.value.status)
        assertNull(viewModel.uiState.value.selectedAlertType)
        assertEquals(EvidencePhase.IDLE, viewModel.evidence.phase)
    }

    @Test
    fun `tras reiniciar no se puede reenviar con el intento perdido`() = runTest {
        alertRepository.result = SendAlertResult.Failed(SendAlertError.AttemptNotFound)
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()
        viewModel.onReportRequested()
        viewModel.onFlowRestarted()

        viewModel.onAlertRetry()

        assertEquals(1, alertRepository.received.size)
    }

    @Test
    fun `una cuenta en revision descarta las fotos`() = runTest {
        alertRepository.result = SendAlertResult.Failed(SendAlertError.UnderReview)
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()

        viewModel.onReportRequested()

        assertEquals(ScanStatus.AlertFailed(SendAlertError.UnderReview), viewModel.uiState.value.status)
        assertTrue(rasterFactory.allWiped())
    }

    @Test
    fun `un error de evidencia conserva las fotos hasta que el usuario cierra`() = runTest {
        alertRepository.result = SendAlertResult.Failed(SendAlertError.InvalidEvidence)
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()

        viewModel.onReportRequested()
        assertTrue(rasterFactory.anyAlive())

        viewModel.onReportCancelled()
        assertTrue(rasterFactory.allWiped())
    }

    @Test
    fun `un doble toque no sube las fotos dos veces`() = runTest {
        val gate = CompletableDeferred<Unit>()
        alertRepository.gate = gate
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()

        viewModel.onReportRequested()
        viewModel.onReportRequested()
        viewModel.onAlertRetry()
        gate.complete(Unit)

        assertEquals(1, alertRepository.received.size)
        assertEquals(ScanStatus.AlertSent, viewModel.uiState.value.status)
    }

    @Test
    fun `tras terminar un envio se puede volver a enviar`() = runTest {
        alertRepository.queue += SendAlertResult.Failed(SendAlertError.Network)
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()
        viewModel.onReportRequested()

        viewModel.onAlertRetry()

        assertEquals(2, alertRepository.received.size)
    }

    @Test
    fun `si renovar un intento vencido dice que el vehiculo ya no esta en la red se reinicia el flujo`() = runTest {
        alertRepository.queue += expired
        attemptRepository.renewResult = StartAlertAttemptResult.NotInNetwork
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()

        viewModel.onReportRequested()

        assertEquals(ScanStatus.AlertFailed(SendAlertError.AttemptNotFound), viewModel.uiState.value.status)
        assertTrue(rasterFactory.allWiped())
    }

    // -- Falla de red al enviar y 404 al reintentar: pudo haberse enviado ---------------------

    private val networkFailure = SendAlertResult.Failed(SendAlertError.Network)
    private val attemptNotFound = SendAlertResult.Failed(SendAlertError.AttemptNotFound)

    @Test
    fun `una falla de red y un 404 al reintentar avisan que pudo haberse enviado, sin reiniciar en silencio`() = runTest {
        alertRepository.queue += networkFailure
        alertRepository.queue += attemptNotFound
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()

        viewModel.onReportRequested()
        assertEquals(ScanStatus.AlertFailed(SendAlertError.Network), viewModel.uiState.value.status)

        viewModel.onAlertRetry()

        assertEquals(ScanStatus.AlertFailed(SendAlertError.PossiblySent), viewModel.uiState.value.status)
        assertEquals(SendAlertRecovery.CLOSE, SendAlertError.PossiblySent.recovery())
    }

    @Test
    fun `el aviso de envio incierto descarta las fotos y no permite reenviar`() = runTest {
        alertRepository.queue += networkFailure
        alertRepository.queue += attemptNotFound
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()
        viewModel.onReportRequested()
        viewModel.onAlertRetry()

        assertTrue(rasterFactory.allWiped())

        viewModel.onAlertRetry()
        viewModel.onReportRequested()

        assertEquals(2, alertRepository.received.size)
        assertEquals(ScanStatus.AlertFailed(SendAlertError.PossiblySent), viewModel.uiState.value.status)
    }

    @Test
    fun `sin una falla de red previa el 404 reinicia el flujo como antes`() = runTest {
        alertRepository.queue += SendAlertResult.Failed(SendAlertError.Unavailable)
        alertRepository.queue += attemptNotFound
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()
        viewModel.onReportRequested()

        viewModel.onAlertRetry()

        assertEquals(ScanStatus.AlertFailed(SendAlertError.AttemptNotFound), viewModel.uiState.value.status)
        assertEquals(SendAlertRecovery.RESTART, SendAlertError.AttemptNotFound.recovery())
    }

    @Test
    fun `un 404 en el primer envio reinicia el flujo, no hay duda de que no se envio`() = runTest {
        alertRepository.queue += attemptNotFound
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()

        viewModel.onReportRequested()

        assertEquals(ScanStatus.AlertFailed(SendAlertError.AttemptNotFound), viewModel.uiState.value.status)
    }

    @Test
    fun `la duda persiste aunque un reintento intermedio falle por otra causa`() = runTest {
        alertRepository.queue += networkFailure
        alertRepository.queue += SendAlertResult.Failed(SendAlertError.Unavailable)
        alertRepository.queue += attemptNotFound
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()
        viewModel.onReportRequested()
        viewModel.onAlertRetry()

        viewModel.onAlertRetry()

        assertEquals(ScanStatus.AlertFailed(SendAlertError.PossiblySent), viewModel.uiState.value.status)
    }

    @Test
    fun `una falla de red tras renovar un intento vencido tambien deja la duda`() = runTest {
        alertRepository.queue += expired
        alertRepository.queue += networkFailure
        alertRepository.queue += attemptNotFound
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()
        viewModel.onReportRequested()
        assertEquals(ScanStatus.AlertFailed(SendAlertError.Network), viewModel.uiState.value.status)

        viewModel.onAlertRetry()

        assertEquals(ScanStatus.AlertFailed(SendAlertError.PossiblySent), viewModel.uiState.value.status)
    }

    @Test
    fun `reiniciar el flujo borra la duda de un envio anterior`() = runTest {
        alertRepository.queue += networkFailure
        alertRepository.queue += attemptNotFound
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()
        viewModel.onReportRequested()
        viewModel.onAlertRetry()
        assertEquals(ScanStatus.AlertFailed(SendAlertError.PossiblySent), viewModel.uiState.value.status)

        viewModel.onFlowRestarted()
        viewModel.onPlateFound("GUQ856")
        viewModel.onPlateConfirmed()
        viewModel.onAlertTypeSelected(AlertType.CRASHED)
        viewModel.completeEvidence()
        alertRepository.queue += attemptNotFound
        viewModel.onReportRequested()

        assertEquals(ScanStatus.AlertFailed(SendAlertError.AttemptNotFound), viewModel.uiState.value.status)
    }

    @Test
    fun `un envio exitoso tras una falla de red no deja aviso`() = runTest {
        alertRepository.queue += networkFailure
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()
        viewModel.onReportRequested()

        viewModel.onAlertRetry()

        assertEquals(ScanStatus.AlertSent, viewModel.uiState.value.status)
    }

    // -- 504: el mismo manejo que un 503 -------------------------------------------------------

    @Test
    fun `una falla transitoria del servidor ofrece reintentar y el reintento funciona`() = runTest {
        // El repositorio traduce 502, 503 y 504 al mismo `Unavailable` (ver AlertWireContractTest).
        alertRepository.queue += SendAlertResult.Failed(SendAlertError.Unavailable)
        val viewModel = withAttempt().capturing(AlertType.CRASHED)
        viewModel.completeEvidence()

        viewModel.onReportRequested()
        assertEquals(ScanStatus.AlertFailed(SendAlertError.Unavailable), viewModel.uiState.value.status)
        assertEquals(SendAlertRecovery.RETRY, SendAlertError.Unavailable.recovery())
        assertTrue(rasterFactory.anyAlive())

        viewModel.onAlertRetry()

        assertEquals(ScanStatus.AlertSent, viewModel.uiState.value.status)
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
