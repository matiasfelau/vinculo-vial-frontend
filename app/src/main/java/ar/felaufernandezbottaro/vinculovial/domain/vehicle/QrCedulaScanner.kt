package ar.felaufernandezbottaro.vinculovial.domain.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CameraFrame
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaScanState
import kotlinx.coroutines.flow.StateFlow

/**
 * Escaneo en vivo del QR de la cédula del vehículo: decodifica el código directo del
 * stream de la cámara, sin sacar ninguna foto — mismo enfoque que `DocumentScanSession`
 * para el DNI. Sin auto-zoom: un QR trae su propia corrección de errores y, a diferencia
 * de un PDF417, se lee cómodo a la distancia normal de encuadre.
 *
 * [CameraFrame] es el mismo modelo neutral que ya comparten la prueba de vida y el escaneo
 * del DNI (US-01): es formato de cámara, no algo específico del registro de usuario.
 */
interface QrCedulaScanner {

    val state: StateFlow<CedulaScanState>

    /** Arranca (o reinicia) el escaneo. */
    fun start()

    /**
     * Entrega un cuadro del preview. La implementación puede descartarlo si ya se
     * decodificó la cédula.
     */
    suspend fun submitFrame(frame: CameraFrame)

    /** Libera el detector. */
    fun close()
}
