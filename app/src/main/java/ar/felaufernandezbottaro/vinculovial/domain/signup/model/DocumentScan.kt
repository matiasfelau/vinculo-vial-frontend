package ar.felaufernandezbottaro.vinculovial.domain.signup.model

/**
 * Qué está viendo el escáner del documento, cuadro a cuadro.
 *
 * A diferencia del modelo anterior (`DocumentPresence`), acá no hay un estado "quieto y
 * listo para disparar": el código se **decodifica directamente del stream**, así que el
 * escaneo termina en el instante en que ML Kit lo lee. No hay foto que tomar ni pulso que
 * congelar — ver `DocumentScanSession`.
 */
data class DocumentScanState(
    val stage: DocumentScanStage = DocumentScanStage.SEARCHING,
    /** Sólo presente en [DocumentScanStage.DECODED]. */
    val document: DocumentIdentity? = null,
) {
    val isDone: Boolean get() = stage == DocumentScanStage.DECODED && document != null
}

enum class DocumentScanStage {
    /** No se ve ningún código en el cuadro. */
    SEARCHING,

    /**
     * Se ve un código pero todavía no se pudo leer — típicamente porque está lejos. ML Kit
     * pide zoom en este estado y el escaneo se resuelve solo en cuestión de cuadros; existe
     * para poder decirle al usuario que la app ya lo vio, en vez de dejarlo dudando.
     */
    CODE_DETECTED,

    /** Se leyó un código, pero no es un DNI argentino de los que sabemos interpretar. */
    NOT_A_DNI,

    /**
     * Se leyó el QR del DNI electrónico, cuyo formato RENAPER no publicó todavía (ver
     * `ElectronicDniQrParser`). Es distinto de [NOT_A_DNI]: el documento es válido, la app
     * es la que no sabe leerlo, y el mensaje al usuario tiene que decir eso.
     */
    UNSUPPORTED_DOCUMENT,

    /**
     * Se leyó un DNI pero sin número de trámite. RENAPER lo necesita para identificar contra
     * qué registro verificar; avisarlo acá evita que el usuario complete toda la selfie para
     * que recién al final se rechace el registro.
     */
    MISSING_PROCEDURE_NUMBER,

    /** Listo: [DocumentScanState.document] tiene los datos. */
    DECODED,
}
