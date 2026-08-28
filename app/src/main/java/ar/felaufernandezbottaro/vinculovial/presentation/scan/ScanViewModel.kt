package ar.felaufernandezbottaro.vinculovial.presentation.scan

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.data.scan.FrameCropper
import ar.felaufernandezbottaro.vinculovial.data.scan.PlateRegexFilter
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.CheckVehicleResult
import ar.felaufernandezbottaro.vinculovial.domain.scan.CheckVehicleUseCase
import ar.felaufernandezbottaro.vinculovial.domain.scan.PlateRecognitionStrategy
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertResult
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertUseCase
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
 * de US-05 (check + alert). Sólo `PlateRecognitionStrategy` es una interfaz de `domain`: el
 * recorte y el filtro de formato son transformaciones de `data` sin estado de negocio propio,
 * así que se consumen directo acá -- mismo criterio que las funciones de `CameraInternals` en
 * `presentation.camera`.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val frameCropper: FrameCropper,
    private val recognitionStrategy: PlateRecognitionStrategy,
    private val regexFilter: PlateRegexFilter,
    private val checkVehicle: CheckVehicleUseCase,
    private val sendAlert: SendAlertUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** Un frame de análisis por vez. */
    private val frameInFlight = Mutex()

    /** Barrera sincrónica contra disparar el mismo check dos veces. */
    private val checkInFlight = AtomicBoolean(false)

    /** El `pat_enc` que ya devolvió el check, a la espera de que el usuario elija un tipo de incidente. */
    private var pendingPatEnc: String? = null

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

            when (val result = checkVehicle(patente)) {
                is CheckVehicleResult.Found -> {
                    pendingPatEnc = result.patEnc
                    _uiState.update { it.copy(status = ScanStatus.AwaitingAlertType) }
                }
                CheckVehicleResult.NotInNetwork ->
                    _uiState.update { it.copy(status = ScanStatus.NotInNetwork) }
                is CheckVehicleResult.Failed ->
                    _uiState.update { it.copy(status = ScanStatus.CheckFailed(result.error)) }
            }

            checkInFlight.set(false)
        }
    }

    /**
     * Elegida en el `ReportChatScreen`: se guarda en [ScanUiState.selectedAlertType] para
     * renderizarla como el mensaje del usuario en el hilo, y se manda con el mismo `pat_enc`
     * que ya devolvió el check -- nunca uno nuevo.
     */
    fun onAlertTypeSelected(alertType: AlertType) {
        val patEnc = pendingPatEnc ?: return
        _uiState.update { it.copy(selectedAlertType = alertType) }
        submitAlert(alertType, patEnc)
    }

    /** Reintenta con el mismo tipo ya elegido -- no vuelve a mostrar el catálogo. */
    fun onAlertRetry() {
        val alertType = _uiState.value.selectedAlertType ?: return
        val patEnc = pendingPatEnc ?: return
        submitAlert(alertType, patEnc)
    }

    private fun submitAlert(alertType: AlertType, patEnc: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(status = ScanStatus.SendingAlert) }

            _uiState.update {
                when (val result = sendAlert(alertType, patEnc)) {
                    SendAlertResult.Sent -> it.copy(status = ScanStatus.AlertSent)
                    is SendAlertResult.Failed -> it.copy(status = ScanStatus.AlertFailed(result.error))
                }
            }
        }
    }

    /** Cierra el mensaje neutro de "no está en la red" y retoma el escaneo. */
    fun onNotInNetworkDismissed() = resumeScanning()

    /** Cierra el error del check (previo a la navegación al chat) y retoma el escaneo. */
    fun onCheckErrorDismissed() {
        pendingPatEnc = null
        resumeScanning()
    }

    private fun resumeScanning() {
        _uiState.update { it.copy(status = ScanStatus.Scanning) }
    }

    private companion object {
        const val MANUAL_ENTRY_ERROR = "Formato de patente inválido. Probá con AAA000 o AA000AA."
    }
}
