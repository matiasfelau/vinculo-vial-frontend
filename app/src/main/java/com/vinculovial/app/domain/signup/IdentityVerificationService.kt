package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.EdgeResult

/**
 * Puerto hacia el pipeline Edge. Se define acá para que `domain` no dependa de `data`.
 *
 * El onboarding biométrico necesita tres capturas:
 * 1. reverso del DNI, de donde se lee el número por PDF417;
 * 2. frente del DNI, de donde se extrae la foto impresa del titular;
 * 3. selfie, tomada recién después de superar la prueba de vida.
 *
 * El servicio **toma posesión** de los bytes que recibe: quien los pasa no debe conservar
 * la referencia. Ninguna implementación puede exponer las imágenes hacia afuera, y
 * [verifyAndDiscard] debe liberarlas incluso si falla.
 */
interface IdentityVerificationService {

    /** @param image JPEG del reverso del documento, donde está el código PDF417. */
    suspend fun acceptDniBack(image: ByteArray)

    /** @param image JPEG del frente del documento, con la foto impresa del titular. */
    suspend fun acceptDniFront(image: ByteArray)

    /**
     * @param image JPEG de la selfie.
     * @param livenessPassed si la prueba de vida se superó en el preview. Se pasa
     * explícitamente para que el resultado del desafío no se pierda entre pantallas.
     */
    suspend fun acceptSelfie(image: ByteArray, livenessPassed: Boolean)

    /**
     * Corre lectura de PDF417 y comparación facial, y descarta las imágenes pase lo
     * que pase.
     */
    suspend fun verifyAndDiscard(): EdgeResult

    /** Descarta cualquier captura pendiente (cancelación, back, error). */
    fun discardCaptures()
}
