package com.vinculovial.app.data.edge

import javax.inject.Inject

/** Compara el rostro del DNI con la selfie y decide si hay una persona viva delante. */
interface LivenessDetector {
    fun isLive(dniFrontImage: ByteArray, selfieImage: ByteArray): Boolean
}

/**
 * Mock de Liveness: exige un frente de DNI legible y una selfie con rostro detectado.
 * En producción, acá va el modelo on-device de face match + anti-spoofing.
 */
class MockLivenessDetector @Inject constructor() : LivenessDetector {

    override fun isLive(dniFrontImage: ByteArray, selfieImage: ByteArray): Boolean {
        val dni = MockImageCodec.decode(dniFrontImage) ?: return false
        val selfie = MockImageCodec.decode(selfieImage) ?: return false
        return dni.kind == MockImageCodec.KIND_DNI_FRONT &&
            selfie.kind == MockImageCodec.KIND_SELFIE &&
            selfie.payload == MockImageCodec.FACE_SIGNATURE
    }
}
