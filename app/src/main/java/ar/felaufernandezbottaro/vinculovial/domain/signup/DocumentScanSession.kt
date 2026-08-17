package ar.felaufernandezbottaro.vinculovial.domain.signup

import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CameraFrame
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentScanState
import kotlinx.coroutines.flow.StateFlow

/**
 * Escaneo del DNI **en vivo**: decodifica el código directo del stream de la cámara, sin
 * sacar ninguna foto.
 *
 * ## Por qué esto sí funciona, cuando la detección en vivo había fracasado antes
 *
 * En este proyecto se intentó dos veces leer el documento desde el preview y se descartó
 * las dos: el stream no tenía suficientes píxeles sobre el código (un PDF417 necesita del
 * orden de 2-3 píxeles por módulo). La conclusión de entonces fue que hacía falta una foto
 * fija de alta resolución, y de ahí salió la arquitectura híbrida.
 *
 * Esa conclusión razonaba con un campo de visión **fijo**. La pieza que faltaba es el
 * auto-zoom de ML Kit ([ZoomController]): cuando el escáner ve un código que no logra
 * decodificar por lejanía, calcula y sugiere la relación de zoom exacta que lo haría
 * legible, y la app la aplica sobre la cámara. El código pasa a ocupar mucha más superficie
 * del sensor y los píxeles por módulo suben sin que el usuario mueva la mano ni se acerque.
 * Es resolución conseguida ópticamente en vez de por conteo de píxeles del cuadro completo.
 *
 * ## Consecuencia de diseño: el documento ya no se fotografía
 *
 * Del documento sólo se necesitan `dni`, `sexo` y `tramite`, todos contenidos en la cadena
 * del código — la foto impresa dejó de usarse cuando la comparación facial se movió al
 * backend (pivote RENAPER-Ready). Sin necesidad de imagen no hay `ImageCapture`, no hay
 * `SecureImageBuffer` para el documento y no hay nada que borrar después: los cuadros se
 * analizan y se sueltan. Es Zero Retention por construcción, no por disciplina.
 */
interface DocumentScanSession {

    val state: StateFlow<DocumentScanState>

    /**
     * Conecta el control de zoom de la cámara, que ML Kit va a manejar solo.
     *
     * Se pasa desde `presentation` porque el zoom es una operación de CameraX y `domain` no
     * puede depender de esa API. La sesión lo usa, no lo decide: quien calcula la relación
     * es ML Kit.
     */
    fun attachZoomController(controller: ZoomController)

    /** Arranca (o reinicia) el escaneo. */
    fun start()

    /**
     * Entrega un cuadro del preview. La implementación puede descartarlo si viene demasiado
     * seguido del anterior o si ya se decodificó el documento.
     */
    suspend fun submitFrame(frame: CameraFrame)

    /** Libera el detector. */
    fun close()
}

/**
 * Puente hacia el zoom de la cámara. Lo implementa `presentation` sobre CameraX; lo invoca
 * ML Kit a través de la sesión.
 */
fun interface ZoomController {

    /**
     * @param ratio relación de zoom sugerida por ML Kit.
     * @return `true` si se pudo aplicar. Devolver `false` le indica al escáner que siga
     * intentando sin zoom, en vez de quedarse esperando un acercamiento que nunca ocurrió.
     */
    fun requestZoom(ratio: Float): Boolean
}
