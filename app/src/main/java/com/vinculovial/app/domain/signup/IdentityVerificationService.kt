package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.EdgeResult

/**
 * Puerto hacia el pipeline Edge. Se define acá para que `domain` no dependa de `data`.
 *
 * Contrato de Zero Retention: ninguna implementación puede exponer los bytes capturados
 * hacia afuera, y [verifyAndDiscard] debe liberar las imágenes incluso si falla.
 */
interface IdentityVerificationService {

    /**
     * Captura el frente del DNI.
     *
     * @param claimedDni el número tipeado por el usuario. Sólo lo usa el mock de cámara para
     * sintetizar una foto verosímil; con OCR real este parámetro desaparece.
     */
    suspend fun captureDniFront(claimedDni: String)

    suspend fun captureSelfie()

    /** Corre OCR + Liveness y descarta las imágenes, pase lo que pase. */
    suspend fun verifyAndDiscard(): EdgeResult

    /** Descarta cualquier captura pendiente (cancelación, back, error). */
    fun discardCaptures()
}
