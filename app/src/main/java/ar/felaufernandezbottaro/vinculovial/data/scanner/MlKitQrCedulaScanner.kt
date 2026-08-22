package ar.felaufernandezbottaro.vinculovial.data.scanner

import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CameraFrame
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.QrCedulaScanner
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaScanStage
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Escaneo en vivo con ML Kit Barcode Scanning, acotado a QR.
 *
 * A diferencia de `MlKitDocumentScanSession` no hay auto-zoom ni despacho por formato: un
 * QR trae su propia corrección de errores y se lee cómodo al encuadre normal, y acá sólo
 * interesa un tipo de código.
 */
@Singleton
class MlKitQrCedulaScanner @Inject constructor(
    private val parser: CedulaQrParser,
) : QrCedulaScanner {

    private val _state = MutableStateFlow(CedulaScanState())
    override val state: StateFlow<CedulaScanState> = _state.asStateFlow()

    /** Un cuadro por vez: ML Kit no admite llamadas concurrentes sobre el mismo cliente. */
    private val inFlight = Mutex()
    private var running = false

    /**
     * NO es `by lazy` — mismo motivo que en `MlKitDocumentScanSession`: esta sesión es
     * `@Singleton` pero el cliente de ML Kit sólo debe vivir lo que dura una visita a la
     * pantalla de escaneo. Se reconstruye explícitamente en [start].
     */
    @Volatile
    private var scanner: BarcodeScanner? = null

    private fun newScanner(): BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    override fun start() {
        // Por si start() se llamara dos veces sin un close() en el medio.
        scanner?.close()
        scanner = newScanner()
        running = true
        _state.value = CedulaScanState()
    }

    override suspend fun submitFrame(frame: CameraFrame) {
        val client = scanner
        if (!running || client == null || _state.value.isDone) return
        if (!inFlight.tryLock()) return

        try {
            val barcodes = client.process(frame.toInputImage()).await()
            if (running) _state.value = evaluate(barcodes)
        } catch (e: Exception) {
            // Un cuadro que falla no invalida el intento: se ignora y sigue el siguiente.
        } finally {
            inFlight.unlock()
        }
    }

    override fun close() {
        running = false
        scanner?.close()
        scanner = null
    }

    private fun evaluate(barcodes: List<Barcode>): CedulaScanState {
        if (barcodes.isEmpty()) return CedulaScanState(CedulaScanStage.SEARCHING)

        barcodes.mapNotNull { it.rawValue }.forEach { raw ->
            parser.parse(raw)?.let { cedula ->
                return CedulaScanState(stage = CedulaScanStage.DECODED, cedula = cedula)
            }
        }

        return CedulaScanState(CedulaScanStage.NOT_A_CEDULA)
    }

    private fun CameraFrame.toInputImage(): InputImage =
        InputImage.fromByteArray(nv21, width, height, rotationDegrees, InputImage.IMAGE_FORMAT_NV21)
}
