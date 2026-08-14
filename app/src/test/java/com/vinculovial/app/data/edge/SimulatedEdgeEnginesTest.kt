package com.vinculovial.app.data.edge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El rechazo de capturas inservibles es lógica real; la extracción todavía es simulada.
 * Estos tests cubren lo primero, que es lo que protege al usuario de un error confuso.
 */
class SimulatedEdgeEnginesTest {

    private val ocr = SimulatedOcrEngine()
    private val liveness = SimulatedLivenessDetector()

    @Test
    fun `una foto de documento utilizable da por leido el dni`() {
        assertEquals("12345678", ocr.extractDni(FakeJpeg.usableDocument(), "12345678"))
    }

    @Test
    fun `una captura vacia no es legible`() {
        assertNull(ocr.extractDni(ByteArray(0), "12345678"))
    }

    @Test
    fun `bytes que no son una foto no son legibles`() {
        assertNull(ocr.extractDni(ByteArray(50_000), "12345678"))
    }

    @Test
    fun `una foto demasiado liviana se descarta`() {
        // Un JPEG de 1 KB con escena real no existe: es un frame en negro o truncado.
        val casiVacia = FakeJpeg.of(width = 1920, height = 1080, sizeBytes = 1_000)

        assertNull(ocr.extractDni(casiVacia, "12345678"))
    }

    @Test
    fun `una foto de baja resolucion no sirve para leer un documento`() {
        val chica = FakeJpeg.of(width = 320, height = 240)

        assertNull(ocr.extractDni(chica, "12345678"))
    }

    @Test
    fun `un dni con formato invalido no se da por leido`() {
        assertNull(ocr.extractDni(FakeJpeg.usableDocument(), "123"))
        assertNull(ocr.extractDni(FakeJpeg.usableDocument(), ""))
    }

    @Test
    fun `liveness acepta dos capturas utilizables`() {
        assertTrue(liveness.isLive(FakeJpeg.usableDocument(), FakeJpeg.usableSelfie()))
    }

    @Test
    fun `liveness rechaza si la selfie no es una foto`() {
        assertFalse(liveness.isLive(FakeJpeg.usableDocument(), ByteArray(0)))
    }

    @Test
    fun `liveness rechaza si el documento es ilegible`() {
        val chico = FakeJpeg.of(width = 320, height = 240)

        assertFalse(liveness.isLive(chico, FakeJpeg.usableSelfie()))
    }

    @Test
    fun `la selfie tolera menos resolucion que el documento`() {
        // 0.48 MP: alcanza para un rostro, no para leer el número de un DNI.
        val media = FakeJpeg.of(width = 800, height = 600)

        assertTrue(CaptureQuality.isUsableSelfie(media))
        assertFalse(CaptureQuality.isUsableDocument(media))
    }
}
