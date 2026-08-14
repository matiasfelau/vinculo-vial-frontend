package com.vinculovial.app.data.edge

import javax.inject.Inject

/** Compara el rostro del DNI con la selfie y decide si hay una persona viva delante. */
interface LivenessDetector {
    fun isLive(dniFrontImage: ByteArray, selfieImage: ByteArray): Boolean
}

/**
 * Liveness simulado: exige que ambas capturas sean fotos utilizables de verdad y da por
 * válida la correspondencia entre ellas.
 *
 * Igual que en [SimulatedOcrEngine], el rechazo es real (una captura rota o en negro no
 * pasa) pero la comparación no: no hay face match ni anti-spoofing hasta que se integre
 * el modelo on-device. Reemplazar esta clase alcanza; nadie más depende de ella.
 */
class SimulatedLivenessDetector @Inject constructor() : LivenessDetector {

    override fun isLive(dniFrontImage: ByteArray, selfieImage: ByteArray): Boolean =
        CaptureQuality.isUsableDocument(dniFrontImage) && CaptureQuality.isUsableSelfie(selfieImage)
}
