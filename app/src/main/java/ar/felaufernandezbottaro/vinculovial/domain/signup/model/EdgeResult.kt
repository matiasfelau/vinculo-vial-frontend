package ar.felaufernandezbottaro.vinculovial.domain.signup.model

/**
 * Resultado del procesamiento local del onboarding — ya no de la "verificación biométrica
 * completa", que se movió al backend (RENAPER). Lo que corre en el dispositivo ahora es:
 * lectura del código de barras del documento, prueba de vida sobre la selfie, y preparar la
 * selfie para viajar. Ninguna comparación de caras ocurre acá.
 *
 * Es lo único que sobrevive a las imágenes: los bytes de las dos capturas se sobreescriben
 * apenas se produce este objeto — con una única excepción deliberada, [Verified.selfieBase64],
 * que por diseño **tiene** que salir del dispositivo (ver `CLAUDE.md`, sección de RENAPER).
 */
sealed interface EdgeResult {

    /**
     * El documento se leyó y la prueba de vida se superó: hay algo para mandar al backend.
     * "Verified" no implica identidad confirmada — sólo que el Edge terminó su parte; la
     * confirmación de identidad es responsabilidad de RENAPER, del lado del servidor.
     *
     * @param document campos leídos del código de barras — no lo que tipeó el usuario.
     * @param selfieBase64 JPEG redimensionado y recomprimido, codificado en Base64, listo
     * para entrar en el payload cifrado. Es la única imagen que sale del dispositivo.
     */
    data class Verified(
        val document: DocumentIdentity,
        val selfieBase64: String,
    ) : EdgeResult

    data class Rejected(val reason: Reason) : EdgeResult

    /**
     * Los motivos que tenían que ver con leer el código (no se encontró, no era un DNI, le
     * faltaba el trámite) ya no están: con el escaneo en vivo esas condiciones se resuelven
     * **durante** el escaneo, con feedback inmediato en pantalla, y el flujo no avanza hasta
     * tener un documento utilizable. Llegar hasta acá sin poder leerlo dejó de ser posible.
     */
    enum class Reason {
        /** La prueba de vida no se completó. */
        LIVENESS_FAILED,

        /** Falta el documento o la selfie. */
        INCOMPLETE_CAPTURE,

        /** La selfie no se pudo decodificar ni comprimir para el envío. */
        SELFIE_UNUSABLE,
    }
}

/**
 * Identificación del documento tal como la necesita el backend para la verificación contra
 * RENAPER. Deliberadamente angosta: el apellido y los nombres del titular no se propagan
 * más allá de la capa que los lee (`LegacyDniPdf417Parser`) — RENAPER identifica por número
 * y trámite, no los necesita para eso.
 */
data class DocumentIdentity(
    /** Número de documento leído del código, no tipeado por el usuario. */
    val number: String,

    /** `M` o `F`, tal como viene en el documento. */
    val sex: String,

    /** Número de trámite. Requerido para que RENAPER pueda resolver la verificación. */
    val procedureNumber: String,
) {
    /** Nunca renderizar los datos del titular. */
    override fun toString(): String = "DocumentIdentity(***)"
}
