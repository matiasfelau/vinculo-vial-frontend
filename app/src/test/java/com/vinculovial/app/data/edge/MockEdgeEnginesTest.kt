package com.vinculovial.app.data.edge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockEdgeEnginesTest {

    private val camera = MockCamera()
    private val ocr = MockOcrEngine()
    private val liveness = MockLivenessDetector()

    private fun dniImage(dni: String = "12345678") =
        MockImageCodec.encode(MockImageCodec.KIND_DNI_FRONT, dni)

    private fun selfieImage(payload: String = MockImageCodec.FACE_SIGNATURE) =
        MockImageCodec.encode(MockImageCodec.KIND_SELFIE, payload)

    @Test
    fun `ocr extrae el dni del frente capturado`() {
        assertEquals("12345678", ocr.extractDni(dniImage()))
    }

    @Test
    fun `ocr no lee un dni de la selfie`() {
        assertNull(ocr.extractDni(selfieImage()))
    }

    @Test
    fun `ocr rechaza bytes que no son una imagen`() {
        assertNull(ocr.extractDni(ByteArray(0)))
        assertNull(ocr.extractDni(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `ocr rechaza un documento ilegible`() {
        assertNull(ocr.extractDni(dniImage("ilegible")))
    }

    @Test
    fun `liveness acepta dni legible mas selfie con rostro`() {
        assertTrue(liveness.isLive(dniImage(), selfieImage()))
    }

    @Test
    fun `liveness rechaza si no se detecta rostro en la selfie`() {
        assertFalse(liveness.isLive(dniImage(), selfieImage(payload = "NO_FACE")))
    }

    @Test
    fun `liveness rechaza imagenes invertidas`() {
        assertFalse(liveness.isLive(selfieImage(), dniImage()))
    }

    @Test
    fun `la camara mock entrega buffers seguros ya cargados`() {
        val buffer = camera.captureDniFront("12345678")

        val decoded = buffer.read { MockImageCodec.decode(it) }

        assertEquals(MockImageCodec.KIND_DNI_FRONT, decoded?.kind)
        assertEquals("12345678", decoded?.payload)
        assertFalse(buffer.isCleared)
    }
}
