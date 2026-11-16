package ar.felaufernandezbottaro.vinculovial.presentation.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * Cuánta resolución se conserva al dejar derecha la foto del documento es lo que decide si el PDF417
 * se lee: el submuestreo nunca puede cruzar el piso de [MAX_CAPTURE_DIMENSION_PX], y una foto que ya
 * está derecha y entra holgada no se recomprime (sólo agregaría artefactos sobre el código).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JpegOrientationTest {

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }

    private fun sizeOf(bytes: ByteArray): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth to bounds.outHeight
    }

    // -- sampleSizeForCapture -----------------------------------------------------------------

    @Test
    fun `una imagen de 12 MP entra entera sin submuestrear`() {
        assertEquals(1, sampleSizeForCapture(4000, 3000))
    }

    @Test
    fun `un sensor de 50 MP baja a la mitad y no mas, para no perder el codigo`() {
        // 8160 / 2 = 4080 >= 3000 -> sample 2; 8160 / 4 = 2040 < 3000 -> corta ahí.
        assertEquals(2, sampleSizeForCapture(8160, 6120))
    }

    @Test
    fun `el lado mayor decide, vertical u horizontal`() {
        assertEquals(sampleSizeForCapture(8160, 6120), sampleSizeForCapture(6120, 8160))
    }

    @Test
    fun `una imagen sin tamano no submuestrea`() {
        assertEquals(1, sampleSizeForCapture(0, 0))
        assertEquals(1, sampleSizeForCapture(-1, 5))
    }

    @Test
    fun `el piso es de 3000 px y nunca se cruza`() {
        for (side in listOf(3000, 5999, 6000, 12000, 24000)) {
            val sample = sampleSizeForCapture(side, side / 2)
            assertTrue("sample=$sample para $side", side / sample >= MAX_CAPTURE_DIMENSION_PX)
        }
    }

    // -- rotatedJpeg --------------------------------------------------------------------------

    @Test
    fun `una foto derecha que entra holgada se devuelve tal cual, sin recomprimir`() {
        val original = jpeg(400, 300)

        val result = original.rotatedJpeg(rotationDegrees = 0)

        assertArrayEquals(original, result)
    }

    @Test
    fun `rotar 90 grados intercambia ancho y alto`() {
        val result = jpeg(400, 300).rotatedJpeg(rotationDegrees = 90)

        assertEquals(300 to 400, sizeOf(result))
    }

    @Test
    fun `rotar 180 grados conserva las dimensiones pero recodifica`() {
        val original = jpeg(400, 300)

        val result = original.rotatedJpeg(rotationDegrees = 180)

        assertEquals(400 to 300, sizeOf(result))
    }
}
