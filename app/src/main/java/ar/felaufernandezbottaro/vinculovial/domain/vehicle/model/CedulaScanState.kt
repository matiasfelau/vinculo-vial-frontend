package ar.felaufernandezbottaro.vinculovial.domain.vehicle.model

/**
 * Qué está viendo el escáner del QR de la cédula, cuadro a cuadro.
 *
 * Mismo criterio que `DocumentScanState` (US-01): el código se decodifica directo del
 * stream, sin foto ni obturador, así que el escaneo termina en el instante en que ML Kit
 * lo lee.
 */
data class CedulaScanState(
    val stage: CedulaScanStage = CedulaScanStage.SEARCHING,
    /** Sólo presente en [CedulaScanStage.DECODED]. */
    val cedula: CedulaData? = null,
) {
    val isDone: Boolean get() = stage == CedulaScanStage.DECODED && cedula != null
}

enum class CedulaScanStage {
    /** No se ve ningún código en el cuadro. */
    SEARCHING,

    /** Se leyó un código QR, pero su contenido no tiene la forma de una cédula de vehículo. */
    NOT_A_CEDULA,

    /** Listo: [CedulaScanState.cedula] tiene los datos. */
    DECODED,
}
