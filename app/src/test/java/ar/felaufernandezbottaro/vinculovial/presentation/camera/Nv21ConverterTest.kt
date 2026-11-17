package ar.felaufernandezbottaro.vinculovial.presentation.camera

import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Cada cuadro de la cámara se copia a NV21 (el formato neutral que cruza hacia `domain`) respetando
 * el relleno de las filas y el entrelazado V,U de las crominancias.
 */
class Nv21ConverterTest {

    private class FakePlane(
        private val data: ByteArray,
        private val rowStride: Int,
        private val pixelStride: Int,
    ) : ImageProxy.PlaneProxy {
        override fun getRowStride() = rowStride
        override fun getPixelStride() = pixelStride
        override fun getBuffer(): ByteBuffer = ByteBuffer.wrap(data)
    }

    private class FakeImage(
        private val width: Int,
        private val height: Int,
        private val planes: Array<ImageProxy.PlaneProxy>,
        private val rotation: Int,
    ) : ImageProxy {
        override fun close() = Unit
        override fun getCropRect() = Rect(0, 0, width, height)
        override fun setCropRect(rect: Rect?) = Unit
        override fun getFormat() = ImageFormat.YUV_420_888
        override fun getWidth() = width
        override fun getHeight() = height
        override fun getPlanes() = planes
        override fun getImageInfo(): ImageInfo = object : ImageInfo {
            override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()
            override fun getTimestamp() = 0L
            override fun getRotationDegrees() = rotation
            override fun getSensorToBufferTransformMatrix() = Matrix()
            override fun populateExifData(exifBuilder: ExifData.Builder) = Unit
        }

        @ExperimentalGetImage
        override fun getImage(): Image? = null
    }

    @Test
    fun `menos de tres planos no se puede convertir`() {
        val image = FakeImage(4, 4, arrayOf(FakePlane(ByteArray(16), 4, 1)), rotation = 0)

        assertNull(image.toCameraFrame())
    }

    @Test
    fun `sin relleno copia la luminancia tal cual e intercala V y U`() {
        val y = ByteArray(16) { it.toByte() } // 4x4
        val u = byteArrayOf(101, 102, 103, 104) // 2x2
        val v = byteArrayOf(111, 112, 113, 114)
        val image = FakeImage(
            width = 4,
            height = 4,
            planes = arrayOf(FakePlane(y, 4, 1), FakePlane(u, 2, 1), FakePlane(v, 2, 1)),
            rotation = 90,
        )

        val frame = image.toCameraFrame()

        assertNotNull(frame)
        frame!!
        assertEquals(4, frame.width)
        assertEquals(4, frame.height)
        assertEquals(90, frame.rotationDegrees)
        assertEquals(16 + 8, frame.nv21.size)
        assertArrayEquals(y, frame.nv21.copyOfRange(0, 16))
        // NV21 = V,U,V,U...
        assertArrayEquals(byteArrayOf(111, 101, 112, 102, 113, 103, 114, 104), frame.nv21.copyOfRange(16, 24))
    }

    @Test
    fun `con relleno al final de cada fila lo descarta`() {
        // 4x2 con un row stride de 6: dos bytes de relleno (99) por fila que no deben pasar.
        val y = byteArrayOf(1, 2, 3, 4, 99, 99, 5, 6, 7, 8, 99, 99)
        val u = byteArrayOf(50, 51)
        val v = byteArrayOf(60, 61)
        val image = FakeImage(
            width = 4,
            height = 2,
            planes = arrayOf(FakePlane(y, 6, 1), FakePlane(u, 2, 1), FakePlane(v, 2, 1)),
            rotation = 0,
        )

        val frame = image.toCameraFrame()!!

        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), frame.nv21.copyOfRange(0, 8))
        assertArrayEquals(byteArrayOf(60, 50, 61, 51), frame.nv21.copyOfRange(8, 12))
    }

    @Test
    fun `un pixel stride de dos en las crominancias toma un valor de cada dos`() {
        // Formato semi-planar habitual: U y V intercalados en el mismo buffer, pixelStride = 2.
        val y = ByteArray(4) { 10 } // 2x2
        val uv = byteArrayOf(70, 80) // un solo par para 2x2 (1 pixel de croma)
        val image = FakeImage(
            width = 2,
            height = 2,
            planes = arrayOf(FakePlane(y, 2, 1), FakePlane(uv, 2, 2), FakePlane(byteArrayOf(80, 0), 2, 2)),
            rotation = 270,
        )

        val frame = image.toCameraFrame()!!

        assertEquals(270, frame.rotationDegrees)
        assertEquals(4 + 2, frame.nv21.size)
        assertEquals(80.toByte(), frame.nv21[4]) // V
        assertEquals(70.toByte(), frame.nv21[5]) // U
    }
}
