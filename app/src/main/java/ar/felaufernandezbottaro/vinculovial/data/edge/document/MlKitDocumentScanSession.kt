package ar.felaufernandezbottaro.vinculovial.data.edge.document

import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import ar.felaufernandezbottaro.vinculovial.domain.signup.DocumentScanSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.ZoomController
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CameraFrame
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentIdentity
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentScanStage
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Escaneo en vivo con ML Kit Barcode Scanning, con **auto-zoom**.
 *
 * Dos opciones del escáner hacen todo el trabajo, y conviene entender por qué están las dos:
 *
 * - [BarcodeScannerOptions.Builder.enableAllPotentialBarcodes]: reporta códigos **detectados
 *   pero no decodificados**. Sin esto el escáner es todo-o-nada y no habría forma de saber
 *   que el usuario ya está apuntando al lugar correcto — ni de decírselo.
 * - [BarcodeScannerOptions.Builder.setZoomSuggestionOptions]: cuando esos códigos detectados
 *   están demasiado lejos para leerse, ML Kit calcula la relación de zoom que los haría
 *   legibles y la pide por [ZoomController]. Es lo que vuelve viable leer del stream algo
 *   que a campo de visión completo no tendría suficientes píxeles por módulo.
 *
 * El resultado, para el usuario, es que apunta el teléfono al DNI a una distancia cómoda y
 * la cámara se acerca sola hasta leerlo. No hay obturador, no hay que quedarse quieto y no
 * hay foto: los cuadros se analizan y se sueltan.
 */
@Singleton
class MlKitDocumentScanSession @Inject constructor(
    private val interpreter: DniBarcodeInterpreter,
) : DocumentScanSession {

    private val _state = MutableStateFlow(DocumentScanState())
    override val state: StateFlow<DocumentScanState> = _state.asStateFlow()

    /** Un cuadro por vez: ML Kit no admite llamadas concurrentes sobre el mismo cliente. */
    private val inFlight = Mutex()
    private var running = false

    @Volatile
    private var zoomController: ZoomController? = null

    /**
     * NO es `by lazy`. Esta sesión es `@Singleton` — vive todo lo que vive el proceso —,
     * pero el cliente de ML Kit tiene que vivir sólo lo que dura **una visita** a la
     * pantalla de escaneo. Con `by lazy` el cliente se construye una única vez para todo
     * el proceso; [close] lo cierra al salir de la pantalla (correcto: es lo que evita
     * retener el detector con la cámara ya desvinculada), pero `lazy` nunca lo vuelve a
     * construir. La segunda vez que el usuario entra a esta pantalla, [submitFrame] llama
     * a `process()` sobre un cliente ya cerrado: ML Kit lanza, la excepción cae en el
     * `catch` silencioso de abajo, y el escaneo queda mudo para siempre — sin crashear y
     * sin ningún síntoma visible más que "la cámara no responde". Sólo se recupera
     * matando el proceso, porque eso es lo único que recrea el singleton de Hilt. Por eso
     * el cliente se reconstruye explícitamente en [start] (que la pantalla llama en cada
     * composición nueva) en vez de dejar que un delegate decida cuándo existe.
     */
    @Volatile
    private var scanner: BarcodeScanner? = null

    private fun newScanner(): BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_PDF417, Barcode.FORMAT_QR_CODE)
            .enableAllPotentialBarcodes()
            .setZoomSuggestionOptions(
                ZoomSuggestionOptions.Builder { ratio ->
                    // Devolver false le dice a ML Kit que el zoom no se aplicó, para que
                    // no espere un acercamiento que nunca va a ocurrir y siga probando.
                    zoomController?.requestZoom(ratio) ?: false
                }.build(),
            )
            .build(),
    )

    override fun attachZoomController(controller: ZoomController) {
        zoomController = controller
    }

    override fun start() {
        // Por si start() se llamara dos veces sin un close() en el medio: así nunca queda
        // más de un cliente vivo a la vez.
        scanner?.close()
        scanner = newScanner()
        running = true
        _state.value = DocumentScanState()
    }

    override suspend fun submitFrame(frame: CameraFrame) {
        val client = scanner
        // Una vez decodificado no se sigue analizando: el resultado ya no puede mejorar y
        // seguir procesando sólo gastaría batería mientras la pantalla hace su transición.
        // `client == null` cubre además un cuadro que llega tarde desde el hilo del
        // analyzer, después de que la pantalla ya se fue y close() ya corrió.
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
        zoomController = null
        scanner?.close()
        scanner = null
    }

    /**
     * El orden importa: entre varios códigos en el cuadro gana el que dé un DNI utilizable.
     * Recién si ninguno sirve se reporta el motivo más informativo de los que haya.
     */
    private fun evaluate(barcodes: List<Barcode>): DocumentScanState {
        if (barcodes.isEmpty()) return DocumentScanState(DocumentScanStage.SEARCHING)

        val readings = barcodes.map(interpreter::interpret)

        readings.filterIsInstance<DniBarcodeInterpreter.Reading.Dni>().forEach { reading ->
            val procedureNumber = reading.value.procedureNumber
                ?: return DocumentScanState(DocumentScanStage.MISSING_PROCEDURE_NUMBER)

            return DocumentScanState(
                stage = DocumentScanStage.DECODED,
                document = DocumentIdentity(
                    number = reading.value.number,
                    sex = reading.value.sex,
                    procedureNumber = procedureNumber,
                ),
            )
        }

        return when {
            readings.any { it is DniBarcodeInterpreter.Reading.UnsupportedDocument } ->
                DocumentScanState(DocumentScanStage.UNSUPPORTED_DOCUMENT)

            // `rawValue` nulo es justamente lo que reporta enableAllPotentialBarcodes():
            // hay un código en el cuadro, todavía no se pudo leer. ML Kit ya está pidiendo
            // zoom por su cuenta; acá sólo se refleja en la UI para que el usuario lo sepa.
            barcodes.any { it.rawValue == null } ->
                DocumentScanState(DocumentScanStage.CODE_DETECTED)

            else -> DocumentScanState(DocumentScanStage.NOT_A_DNI)
        }
    }

    private fun CameraFrame.toInputImage(): InputImage =
        InputImage.fromByteArray(nv21, width, height, rotationDegrees, InputImage.IMAGE_FORMAT_NV21)
}
