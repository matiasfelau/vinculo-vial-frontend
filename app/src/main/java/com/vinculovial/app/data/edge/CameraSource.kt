package com.vinculovial.app.data.edge

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente de imágenes del dispositivo. Entrega buffers seguros, nunca arrays desnudos:
 * así los bytes nacen ya dentro del contrato de Zero Retention.
 */
interface CameraSource {
    fun captureDniFront(claimedDni: String): SecureImageBuffer
    fun captureSelfie(): SecureImageBuffer
}

/**
 * Sustituto de la cámara: la captura real (CameraX + guías de encuadre) está fuera del
 * alcance de US-01. Devuelve bytes sintéticos con el mismo contrato que una foto real.
 */
@Singleton
class MockCamera @Inject constructor() : CameraSource {

    override fun captureDniFront(claimedDni: String): SecureImageBuffer =
        SecureImageBuffer.wrap(MockImageCodec.encode(MockImageCodec.KIND_DNI_FRONT, claimedDni))

    override fun captureSelfie(): SecureImageBuffer =
        SecureImageBuffer.wrap(MockImageCodec.encode(MockImageCodec.KIND_SELFIE, MockImageCodec.FACE_SIGNATURE))
}
