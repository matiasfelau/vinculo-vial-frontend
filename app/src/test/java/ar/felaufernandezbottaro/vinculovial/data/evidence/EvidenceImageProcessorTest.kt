package ar.felaufernandezbottaro.vinculovial.data.evidence

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EvidenceImageProcessorTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun processor(factory: EvidenceRasterFactory) = EvidenceImageProcessor(factory, dispatcher)

    private fun processed(result: EvidenceProcessingResult): ByteArray {
        assertTrue("esperaba Processed pero fue $result", result is EvidenceProcessingResult.Processed)
        return (result as EvidenceProcessingResult.Processed).photo.read { it.copyOf() }
    }

    // -- Tamaño, peso y EXIF, parseando el JPEG de salida -------------------------------------

    @Test
    fun `una imagen de 12 MP queda en 1600 px o menos, en 900 KB o menos y sin EXIF`() = runTest {
        val source = syntheticJpeg(4_000, 3_000, withExif = true)
        assertTrue("el JPEG de origen tiene que traer EXIF para que la prueba valga", hasExif(source))

        val jpeg = processed(processor(SyntheticRasterFactory()).process(source, rotationDegrees = 0))

        val info = checkNotNull(jpegInfo(jpeg)) { "la salida no es un JPEG" }
        assertTrue("lado mayor ${maxOf(info.width, info.height)}", maxOf(info.width, info.height) <= 1_600)
        assertEquals(1_600 to 1_200, info.width to info.height)
        assertTrue("pesa ${jpeg.size} bytes", jpeg.size <= 900 * 1024)
        assertFalse("el JPEG de salida conserva el EXIF", info.hasExif)
    }

    @Test
    fun `la salida es un JPEG nuevo, no los bytes de entrada`() = runTest {
        val source = syntheticJpeg(800, 600, payloadBytes = 4_096, withExif = true)
        val original = source.copyOf()

        val jpeg = processed(processor(SyntheticRasterFactory()).process(source, rotationDegrees = 0))

        assertFalse(jpeg.contentEquals(original))
        assertFalse(hasExif(jpeg))
    }

    @Test
    fun `aplica la rotacion antes de acotar el lado mayor`() = runTest {
        val source = syntheticJpeg(3_000, 2_000)

        val jpeg = processed(processor(SyntheticRasterFactory()).process(source, rotationDegrees = 90))

        val info = checkNotNull(jpegInfo(jpeg))
        assertEquals(1_067 to 1_600, info.width to info.height)
    }

    @Test
    fun `una imagen chica no se agranda`() = runTest {
        val source = syntheticJpeg(800, 600)

        val jpeg = processed(processor(SyntheticRasterFactory()).process(source, rotationDegrees = 0))

        assertEquals(800 to 600, checkNotNull(jpegInfo(jpeg)).let { it.width to it.height })
    }

    @Test
    fun `sobreescribe con ceros los bytes de entrada`() = runTest {
        val source = syntheticJpeg(800, 600)
        val size = source.size

        processor(SyntheticRasterFactory()).process(source, rotationDegrees = 0)

        assertArrayEquals(ByteArray(size), source)
    }

    @Test
    fun `libera las imagenes intermedias`() = runTest {
        val factory = SyntheticRasterFactory()

        processor(factory).process(syntheticJpeg(2_400, 1_800), rotationDegrees = 0)

        assertTrue(factory.decoded.isNotEmpty())
        assertTrue(factory.decoded.all { it.isWiped })
    }

    @Test
    fun `bytes que no son una imagen son Unreadable`() = runTest {
        val result = processor(SyntheticRasterFactory()).process(ByteArray(64) { 7 }, rotationDegrees = 0)

        assertEquals(EvidenceProcessingResult.Unreadable, result)
    }

    // -- Bucle de calidad (tamaños programados) --------------------------------------------

    @Test
    fun `baja la calidad de 85 a 60 hasta que entra en 900 KB`() = runTest {
        val raster = StubRaster(sizeForQuality = { quality -> if (quality > 75) LIMIT + 1 else LIMIT - 1 })

        val result = processor(StubRasterFactory(raster)).process(ByteArray(4), rotationDegrees = 0)

        assertEquals(listOf(85, 80, 75), raster.qualitiesTried)
        assertEquals(LIMIT - 1, processed(result).size)
    }

    @Test
    fun `si entra a la primera usa calidad 85`() = runTest {
        val raster = StubRaster(sizeForQuality = { LIMIT })

        processor(StubRasterFactory(raster)).process(ByteArray(4), rotationDegrees = 0)

        assertEquals(listOf(85), raster.qualitiesTried)
    }

    @Test
    fun `si ni a calidad 60 entra, es TooLarge y no queda nada en claro`() = runTest {
        val raster = StubRaster(sizeForQuality = { LIMIT + 1 })

        val result = processor(StubRasterFactory(raster)).process(ByteArray(4), rotationDegrees = 0)

        assertEquals(EvidenceProcessingResult.TooLarge, result)
        assertEquals(listOf(85, 80, 75, 70, 65, 60), raster.qualitiesTried)
        assertTrue(raster.encodedArrays.all { array -> array.all { it == 0.toByte() } })
        assertTrue(raster.isWiped)
    }

    @Test
    fun `descarta con ceros los intentos que no entraron`() = runTest {
        val raster = StubRaster(sizeForQuality = { quality -> if (quality > 80) LIMIT + 1 else LIMIT })

        val result = processor(StubRasterFactory(raster)).process(ByteArray(4), rotationDegrees = 0)

        assertNotNull(processed(result))
        val discarded = raster.encodedArrays.first()
        assertTrue(discarded.all { it == 0.toByte() })
    }

    private class StubRaster(private val sizeForQuality: (Int) -> Int) : EvidenceRaster {
        val qualitiesTried = mutableListOf<Int>()
        val encodedArrays = mutableListOf<ByteArray>()
        var isWiped = false
            private set

        override val width = 100
        override val height = 100

        override fun scaledToFit(maxSide: Int): EvidenceRaster = this

        override fun toJpeg(quality: Int): ByteArray {
            qualitiesTried += quality
            return ByteArray(sizeForQuality(quality)) { 9 }.also { encodedArrays += it }
        }

        override fun wipe() {
            isWiped = true
        }
    }

    private class StubRasterFactory(private val raster: EvidenceRaster) : EvidenceRasterFactory {
        override fun decodeUpright(jpeg: ByteArray, rotationDegrees: Int, targetSide: Int) = raster
    }

    private companion object {
        const val LIMIT = 900 * 1024
    }
}
