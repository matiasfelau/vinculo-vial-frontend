package ar.felaufernandezbottaro.vinculovial.domain.signup

import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentIdentity
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.EdgeResult

/**
 * Puerto hacia el pipeline Edge. Se define acá para que `domain` no dependa de `data`.
 *
 * El onboarding necesita dos cosas:
 * 1. los datos del documento, ya leídos del código de barras por [DocumentScanSession] —
 *    **no una foto**: del documento sólo viajan `dni`/`sexo`/`tramite`, y desde que la
 *    comparación facial se movió al backend no hay ningún uso para su imagen;
 * 2. la selfie, tomada recién después de superar la prueba de vida.
 *
 * El servicio **toma posesión** de los bytes de la selfie: quien los pasa no debe conservar
 * la referencia. Ninguna implementación puede exponer esa imagen hacia afuera, y
 * [verifyAndDiscard] debe liberarla incluso si falla.
 */
interface IdentityVerificationService {

    /**
     * @param document datos leídos del código del DNI. Se aceptan tal cual: la lectura ya
     * ocurrió en el escaneo en vivo y un código decodifica o no decodifica — no hay una
     * "segunda opinión" que un re-análisis pudiera aportar.
     */
    suspend fun acceptDocument(document: DocumentIdentity)

    /**
     * @param image JPEG de la selfie.
     * @param livenessPassed si la prueba de vida se superó en el preview. Se pasa
     * explícitamente para que el resultado del desafío no se pierda entre pantallas.
     */
    suspend fun acceptSelfie(image: ByteArray, livenessPassed: Boolean)

    /**
     * Comprime la selfie para el payload y descarta la original pase lo que pase.
     */
    suspend fun verifyAndDiscard(): EdgeResult

    /** Descarta cualquier captura pendiente (cancelación, back, error). */
    fun discardCaptures()
}
