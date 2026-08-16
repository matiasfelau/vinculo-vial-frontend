package ar.felaufernandezbottaro.vinculovial.domain.signup

import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CameraFrame
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentPresence
import kotlinx.coroutines.flow.StateFlow

/**
 * Detección de **presencia** del documento sobre el stream de la cámara, para disparar la
 * foto sola cuando el DNI está encuadrado y quieto.
 *
 * ## Por qué esto no es la detección en vivo que se había retirado
 *
 * Hubo una versión anterior (`DocumentScanSession`) que intentaba **leer** el documento desde
 * el preview y se retiró porque fallaba de forma inconsistente en hardware real. Esta sesión
 * tiene una responsabilidad estrictamente más chica y es justamente lo que la hace viable:
 * no decodifica nada. Sólo responde si hay una caja de código de barras y una cara en el
 * cuadro, y si dejaron de moverse.
 *
 * La resolución es lo que separa un problema del otro. Decodificar un PDF417 exige del orden
 * de 2.5 píxeles por módulo, que el stream de análisis no tiene a ninguna distancia cómoda —
 * ese fue siempre el motivo real del fracaso anterior, no la heurística. Detectar que *hay*
 * un código, en cambio, ML Kit lo hace incluso sobre uno borroso e ilegible
 * (`enableAllPotentialBarcodes`). La lectura de verdad sigue ocurriendo sobre la foto fija de
 * máxima resolución que dispara esta sesión.
 *
 * Los frames se procesan y se sueltan: la sesión no acumula imágenes.
 */
interface DocumentPresenceSession {

    val presence: StateFlow<DocumentPresence>

    /** Arranca o reanuda el análisis. Reinicia el conteo de estabilidad. */
    fun start()

    /**
     * Entrega un frame del preview. La implementación puede descartarlo si viene demasiado
     * seguido del anterior o si la sesión está pausada.
     */
    suspend fun submitFrame(frame: CameraFrame)

    /**
     * Suspende el análisis mientras se toma y valida la foto.
     *
     * Sin esto la sesión seguiría reportando `STEADY` durante toda la captura y dispararía
     * de nuevo encima de la anterior.
     */
    fun pause()

    /** Libera los recursos de los detectores. */
    fun close()
}
