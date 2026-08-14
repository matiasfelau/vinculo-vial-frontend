package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.EdgeResult

/**
 * Puerto hacia el pipeline Edge. Se define acá para que `domain` no dependa de `data`.
 *
 * La captura no la dispara el dominio: la cámara la maneja `presentation` (necesita
 * ciclo de vida, preview y una acción del usuario) y acá se entregan los bytes ya
 * obtenidos. El servicio **toma posesión** de esos bytes: quien los pasa no debe
 * conservar la referencia.
 *
 * Contrato de Zero Retention: ninguna implementación puede exponer las imágenes hacia
 * afuera, y [verifyAndDiscard] debe liberarlas incluso si falla.
 */
interface IdentityVerificationService {

    /**
     * Entrega la foto del frente del DNI.
     *
     * @param image bytes JPEG. El servicio toma posesión de ellos.
     * @param declaredDni número que tipeó el usuario. Sólo lo consume el motor de OCR
     * simulado; el motor real lee el documento y lo ignora.
     */
    suspend fun acceptDniFront(image: ByteArray, declaredDni: String)

    /** @param image bytes JPEG de la selfie. El servicio toma posesión de ellos. */
    suspend fun acceptSelfie(image: ByteArray)

    /** Corre OCR + Liveness y descarta las imágenes, pase lo que pase. */
    suspend fun verifyAndDiscard(): EdgeResult

    /** Descarta cualquier captura pendiente (cancelación, back, error). */
    fun discardCaptures()
}
