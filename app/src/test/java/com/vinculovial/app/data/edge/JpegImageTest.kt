package com.vinculovial.app.data.edge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JpegImageTest {

    @Test
    fun `reconoce un jpeg por su cabecera`() {
        assertTrue(JpegImage.isJpeg(FakeJpeg.of(width = 640, height = 480)))
    }

    @Test
    fun `rechaza bytes que no son jpeg`() {
        assertFalse(JpegImage.isJpeg(ByteArray(0)))
        assertFalse(JpegImage.isJpeg(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))) // PNG
        assertFalse(JpegImage.isJpeg(ByteArray(100)))
    }

    @Test
    fun `lee las dimensiones salteando segmentos previos`() {
        val dimensions = JpegImage.dimensionsOf(FakeJpeg.of(width = 1920, height = 1080))

        assertEquals(ImageDimensions(width = 1920, height = 1080), dimensions)
    }

    @Test
    fun `calcula los megapixeles`() {
        val dimensions = JpegImage.dimensionsOf(FakeJpeg.of(width = 2000, height = 1000))!!

        assertEquals(2.0, dimensions.megapixels, 0.001)
    }

    @Test
    fun `sin cabecera de dimensiones devuelve null`() {
        // SOI + EOI: es un JPEG en lo formal, pero no declara tamaño.
        val sinSof = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

        assertNull(JpegImage.dimensionsOf(sinSof))
    }

    @Test
    fun `una imagen truncada no rompe el parser`() {
        val completa = FakeJpeg.of(width = 1920, height = 1080)
        val truncada = completa.copyOfRange(0, 12)

        assertNull(JpegImage.dimensionsOf(truncada))
    }

    @Test
    fun `un segmento con longitud invalida corta el barrido`() {
        // FF E0 con longitud 0: avanzar por esa longitud dejaría el parser en bucle.
        val corrupta = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x00,
            0xFF.toByte(), 0xD9.toByte(),
        )

        assertNull(JpegImage.dimensionsOf(corrupta))
    }
}
