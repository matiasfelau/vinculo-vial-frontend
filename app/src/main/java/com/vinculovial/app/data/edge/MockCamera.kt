package com.vinculovial.app.data.edge

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sustituto de la cámara: la captura real (CameraX + guías de encuadre) está fuera del
 * alcance de US-01. Devuelve bytes sintéticos con el mismo contrato que una foto real:
 * los consume el Edge y se descartan enseguida.
 */
@Singleton
class MockCamera @Inject constructor() {

    fun captureDniFront(claimedDni: String): ByteArray =
        MockImageCodec.encode(MockImageCodec.KIND_DNI_FRONT, claimedDni)

    fun captureSelfie(): ByteArray =
        MockImageCodec.encode(MockImageCodec.KIND_SELFIE, MockImageCodec.FACE_SIGNATURE)
}
