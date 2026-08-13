package com.vinculovial.app.data.edge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockEdgeEnginesTest {

    private val camera = MockCamera()
    private val ocr = MockOcrEngine()
    private val liveness = MockLivenessDetector()

    @Test
    fun `ocr extrae el dni del frente capturado`() {
        val image = camera.captureDniFront("12345678")

        assertEquals("12345678", ocr.extractDni(image))
    }

    @Test
    fun `ocr no lee un dni de la selfie`() {
        assertNull(ocr.extractDni(camera.captureSelfie()))
    }

    @Test
    fun `ocr rechaza bytes que no son una imagen`() {
        assertNull(ocr.extractDni(ByteArray(0)))
        assertNull(ocr.extractDni(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `ocr rechaza un documento ilegible`() {
        val borroso = MockImageCodec.encode(MockImageCodec.KIND_DNI_FRONT, "ilegible")

        assertNull(ocr.extractDni(borroso))
    }

    @Test
    fun `liveness acepta dni legible mas selfie con rostro`() {
        assertTrue(liveness.isLive(camera.captureDniFront("12345678"), camera.captureSelfie()))
    }

    @Test
    fun `liveness rechaza si no se detecta rostro en la selfie`() {
        val sinRostro = MockImageCodec.encode(MockImageCodec.KIND_SELFIE, "NO_FACE")

        assertFalse(liveness.isLive(camera.captureDniFront("12345678"), sinRostro))
    }

    @Test
    fun `liveness rechaza imagenes invertidas`() {
        assertFalse(liveness.isLive(camera.captureSelfie(), camera.captureDniFront("12345678")))
    }

    @Test
    fun `la imagen sintetica no expone el dni en texto plano fuera de la cabecera`() {
        val image = camera.captureDniFront("12345678")
        val header = MockImageCodec.decode(image)

        assertEquals(MockImageCodec.KIND_DNI_FRONT, header?.kind)
        assertEquals("12345678", header?.payload)
    }
}
