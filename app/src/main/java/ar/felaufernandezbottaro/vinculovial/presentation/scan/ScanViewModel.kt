package ar.felaufernandezbottaro.vinculovial.presentation.scan

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.data.edge.SecureBitmap
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceBuffers
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceImageProcessor
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceProcessingResult
import ar.felaufernandezbottaro.vinculovial.data.scan.FrameCropper
import ar.felaufernandezbottaro.vinculovial.data.scan.PlateRegexFilter
import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidencePlan
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.PlateRecognitionStrategy
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertResult
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertUseCase
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptResult
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptUseCase
import ar.felaufernandezbottaro.vinculovial.presentation.evidence.GalleryEvidenceSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Orquesta el pipeline de escaneo (recorte + OCR + filtro de formato) y el flujo de dos pasos
 * de US-05/US-08 (intento + alert). Sólo `PlateRecognitionStrategy` es una interfaz de `domain`: el
 * recorte y el filtro de formato son transformaciones de `data` sin estado de negocio propio,
 * así que se consumen directo acá -- mismo criterio que las funciones de `CameraInternals` en
 * `presentation.camera`.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val frameCropper: FrameCropper,
    private val recognitionStrategy: PlateRecognitionStrategy,
    private val regexFilter: PlateRegexFilter,
    private val startAlertAttempt: StartAlertAttemptUseCase,
    private val sendAlert: SendAlertUseCase,
    private val evidenceProcessor: EvidenceImageProcessor,
    /** Sólo `debug` ofrece galería: en release es la no-op. */
    val galleryEvidenceSource: GalleryEvidenceSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** Un frame de análisis por vez. */
    private val frameInFlight = Mutex()

    /** Barrera sincrónica contra disparar el mismo check dos veces. */
    private val checkInFlight = AtomicBoolean(false)

    /** Barrera sincrónica contra subir las fotos dos veces con un doble toque. */
    private val sendInFlight = AtomicBoolean(false)

    /** El intento que ya abrió el servidor, a la espera de que el usuario elija un tipo de incidente. */
    private var pendingAttempt: StartAlertAttemptResult.Started? = null

    /**
     * Las fotos de evidencia ya procesadas (JPEG en claro, sin EXIF). Se liberan al enviar con
     * éxito, al cancelar y en [onCleared].
     */
    private val evidenceBuffers = EvidenceBuffers()

    /**
     * Recorta, corre OCR y filtra un frame del análisis en vivo. Se llama desde el hilo del
     * analizador de CameraX (no el hilo principal) -- mismo patrón que
     * `MlKitQrCedulaScanner.submitFrame`. Sólo actualiza [uiState] a [ScanStatus.Detected]:
     * a partir de ahí el pipeline se pausa (ya no corre porque el status deja de ser
     * `Scanning`) hasta que el humano confirme o rechace lo que leyó el OCR -- ver
     * [onPlateConfirmed] / [onPlateRejected].
     */
    suspend fun processFrame(imageProxy: ImageProxy) {
        if (_uiState.value.status !is ScanStatus.Scanning || !frameInFlight.tryLock()) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = frameCropper.crop(imageProxy)
            imageProxy.close()
            if (bitmap == null) return

            val candidates = try {
                recognitionStrategy.recognize(bitmap)
            } finally {
                bitmap.recycle()
            }

            val patente = regexFilter.filter(candidates)
            if (patente != null) onPlateFound(patente)
        } finally {
            frameInFlight.unlock()
        }
    }

    /**
     * Separado de [processFrame] para que los tests puedan ejercitar la máquina de estados
     * desde una patente ya reconocida, sin necesitar un `ImageProxy`/`Bitmap` real (no existen
     * en un test JVM plano).
     */
    internal fun onPlateFound(patente: String) {
        if (_uiState.value.status is ScanStatus.Scanning) {
            _uiState.update { it.copy(status = ScanStatus.Detected(patente)) }
        }
    }

    /** El humano confirmó que el OCR leyó bien: recién acá arranca el check contra el backend. */
    fun onPlateConfirmed() {
        val detected = _uiState.value.status as? ScanStatus.Detected ?: return
        runCheck(detected.patente)
    }

    /** El humano marcó que el OCR se equivocó: ofrece reintentar o tipear la patente a mano. */
    fun onPlateRejected() {
        _uiState.update { it.copy(status = ScanStatus.Rejected) }
    }

    /** Descarta la lectura rechazada y retoma el escaneo por cámara. */
    fun onRescan() = resumeScanning()

    fun onManualEntryOpened() {
        _uiState.update { it.copy(status = ScanStatus.ManualEntry()) }
    }

    /**
     * Valida [rawText] con el mismo `PlateRegexFilter` que usa el pipeline de cámara. Una
     * patente tipeada válida entra al mismo embudo criptográfico que una confirmada por
     * cámara ([runCheck]) -- el caso de uso no distingue el origen del dato, sólo recibe un
     * `String` ya normalizado. Ya la escribió el humano a propósito, así que no hace falta
     * un paso de confirmación extra: tipear ES la confirmación.
     */
    fun onManualPlateSubmitted(rawText: String) {
        val patente = regexFilter.filter(listOf(rawText))
        if (patente == null) {
            _uiState.update { it.copy(status = ScanStatus.ManualEntry(error = MANUAL_ENTRY_ERROR)) }
            return
        }
        runCheck(patente)
    }

    /** Cierra el ingreso manual sin verificar nada y retoma el escaneo por cámara. */
    fun onManualEntryDismissed() = resumeScanning()

    private fun runCheck(patente: String) {
        if (!checkInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            _uiState.update { it.copy(status = ScanStatus.Checking) }

            when (val result = startAlertAttempt(patente)) {
                is StartAlertAttemptResult.Started -> {
                    pendingAttempt = result
                    _uiState.update { it.copy(status = ScanStatus.AwaitingAlertType) }
                }
                StartAlertAttemptResult.NotInNetwork ->
                    _uiState.update { it.copy(status = ScanStatus.NotInNetwork) }
                is StartAlertAttemptResult.Failure ->
                    _uiState.update { it.copy(status = ScanStatus.CheckFailed(result)) }
            }

            checkInFlight.set(false)
        }
    }

    /**
     * Elegida en el `ReportChatScreen`: se guarda en [ScanUiState.selectedAlertType] para
     * renderizarla como el mensaje del usuario en el hilo, y arranca la captura guiada de
     * evidencia de ese tipo. Un tipo que la app no ofrece se ignora.
     */
    fun onAlertTypeSelected(alertType: AlertType) {
        if (pendingAttempt == null || !alertType.isOffered) return

        evidenceBuffers.clear()
        _uiState.update {
            it.copy(
                selectedAlertType = alertType,
                evidence = EvidenceCaptureState(
                    steps = EvidencePlan.stepsFor(alertType),
                    phase = EvidencePhase.CAPTURING,
                ),
            )
        }
    }

    /**
     * Una foto del paso actual, de la cámara o (sólo en `debug`) de la galería: las dos entran
     * por acá y pasan por el mismo pipeline. [jpeg] se sobreescribe con ceros al terminar.
     */
    fun onPhotoCaptured(jpeg: ByteArray, rotationDegrees: Int) {
        val evidence = _uiState.value.evidence
        val step = evidence.currentStep
        if (evidence.phase != EvidencePhase.CAPTURING || step == null) {
            jpeg.fill(0)
            return
        }

        _uiState.update { it.copy(evidence = it.evidence.copy(phase = EvidencePhase.PROCESSING, error = null)) }

        viewModelScope.launch {
            when (val result = evidenceProcessor.process(jpeg, rotationDegrees)) {
                is EvidenceProcessingResult.Processed -> {
                    evidenceBuffers.put(step.role, evidence.photoIndex, result.photo)
                    _uiState.update {
                        it.copy(
                            evidence = it.evidence.copy(
                                phase = EvidencePhase.REVIEWING,
                                reviewVersion = it.evidence.reviewVersion + 1,
                            ),
                        )
                    }
                }
                EvidenceProcessingResult.Unreadable -> failCapture(EvidenceError.UNREADABLE)
                EvidenceProcessingResult.TooLarge -> failCapture(EvidenceError.TOO_LARGE)
            }
        }
    }

    /** La cámara no entregó la foto: se queda en el mismo paso para reintentar. */
    fun onPhotoCaptureFailed() = failCapture(EvidenceError.CAPTURE_FAILED)

    private fun failCapture(error: EvidenceError) {
        _uiState.update { it.copy(evidence = it.evidence.copy(phase = EvidencePhase.CAPTURING, error = error)) }
    }

    /** Descarta la foto en revisión y vuelve a la cámara en el mismo casillero. */
    fun onPhotoRetake() {
        val evidence = _uiState.value.evidence
        val step = evidence.currentStep ?: return
        if (evidence.phase != EvidencePhase.REVIEWING) return

        evidenceBuffers.remove(step.role, evidence.photoIndex)
        _uiState.update { it.copy(evidence = it.evidence.copy(phase = EvidencePhase.CAPTURING, error = null)) }
    }

    /** Sólo el daño admite más de una foto (hasta 3): abre un casillero nuevo en el mismo paso. */
    fun onAddAnotherPhoto() {
        val evidence = _uiState.value.evidence
        if (evidence.phase != EvidencePhase.REVIEWING || !evidence.canAddAnother) return

        _uiState.update {
            it.copy(
                evidence = it.evidence.copy(
                    photoIndex = it.evidence.photoIndex + 1,
                    phase = EvidencePhase.CAPTURING,
                    error = null,
                ),
            )
        }
    }

    /** Da por buena la foto en revisión y pasa al paso siguiente, o termina si era el último. */
    fun onStepConfirmed() {
        val evidence = _uiState.value.evidence
        if (evidence.phase != EvidencePhase.REVIEWING) return

        _uiState.update {
            it.copy(
                evidence = if (evidence.isLastStep) {
                    evidence.copy(phase = EvidencePhase.COMPLETE)
                } else {
                    evidence.copy(
                        stepIndex = evidence.stepIndex + 1,
                        photoIndex = 0,
                        phase = EvidencePhase.CAPTURING,
                        error = null,
                    )
                },
            )
        }
    }

    /** Sale de la captura sin enviar: libera las fotos y vuelve a elegir el tipo. */
    fun onEvidenceDiscarded() {
        evidenceBuffers.clear()
        _uiState.update { it.copy(selectedAlertType = null, evidence = EvidenceCaptureState()) }
    }

    /** Cancela el reporte entero: libera las fotos. */
    fun onReportCancelled() = onEvidenceDiscarded()

    /**
     * Decodifica la foto en revisión para mostrarla en memoria. El llamador debe liberar el
     * `Bitmap` con `SecureBitmap.wipe`. `null` si no hay foto en ese casillero.
     */
    fun decodeReviewPhoto(maxSidePx: Int = REVIEW_PREVIEW_PX): Bitmap? {
        val evidence = _uiState.value.evidence
        val step = evidence.currentStep ?: return null
        return evidenceBuffers.read(step.role, evidence.photoIndex) { SecureBitmap.decode(it, maxSidePx) }
    }

    /** Con la evidencia completa, manda el reporte: las fotos se cifran para IA con el `attempt_id` vigente. */
    fun onReportRequested() {
        if (_uiState.value.evidence.phase != EvidencePhase.COMPLETE) return
        submitAlert()
    }

    /**
     * Reintento manual (429, 503, red, error inesperado): reenvía con las mismas fotos, que se
     * conservan hasta el envío exitoso o la cancelación. Si el intento venció, se renueva solo.
     */
    fun onAlertRetry() = submitAlert()

    /** 404 `ATTEMPT_NOT_FOUND`: el intento se perdió. Se descarta todo y se vuelve a escanear la patente. */
    fun onFlowRestarted() {
        evidenceBuffers.clear()
        pendingAttempt = null
        _uiState.value = ScanUiState()
    }

    private fun submitAlert() {
        val alertType = _uiState.value.selectedAlertType ?: return
        val attempt = pendingAttempt ?: return
        if (!sendInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(status = ScanStatus.SendingAlert) }

                val outcome = sendAlert(attempt, alertType, evidenceBuffers)
                pendingAttempt = outcome.attempt

                when (val result = outcome.result) {
                    SendAlertResult.Sent -> {
                        evidenceBuffers.clear()
                        _uiState.update { it.copy(status = ScanStatus.AlertSent) }
                    }
                    is SendAlertResult.Failed -> {
                        // Sin intento vigente o sin permiso de enviar no hay reenvío posible:
                        // las fotos ya no sirven.
                        if (result.error == SendAlertError.AttemptNotFound || result.error == SendAlertError.UnderReview) {
                            evidenceBuffers.clear()
                        }
                        _uiState.update { it.copy(status = ScanStatus.AlertFailed(result.error)) }
                    }
                }
            } finally {
                sendInFlight.set(false)
            }
        }
    }

    /** Cierra el mensaje neutro de "no está en la red" y retoma el escaneo. */
    fun onNotInNetworkDismissed() = resumeScanning()

    /** Cierra el error del check (previo a la navegación al chat) y retoma el escaneo. */
    fun onCheckErrorDismissed() {
        pendingAttempt = null
        resumeScanning()
    }

    private fun resumeScanning() {
        _uiState.update { it.copy(status = ScanStatus.Scanning) }
    }

    override fun onCleared() {
        evidenceBuffers.clear()
        super.onCleared()
    }

    private companion object {
        const val REVIEW_PREVIEW_PX = 1_200
        const val MANUAL_ENTRY_ERROR = "Formato de patente inválido. Probá con AAA000 o AA000AA."
    }
}
