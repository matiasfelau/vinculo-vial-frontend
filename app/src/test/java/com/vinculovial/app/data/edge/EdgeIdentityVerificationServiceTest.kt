package com.vinculovial.app.data.edge

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EdgeIdentityVerificationServiceTest {

    /** Se queda con los buffers entregados para poder auditar que quedaron liberados. */
    private class RecordingCameraSource(
        private val delegate: CameraSource = MockCamera(),
    ) : CameraSource {
        val entregados = mutableListOf<SecureImageBuffer>()

        override fun captureDniFront(claimedDni: String): SecureImageBuffer =
            delegate.captureDniFront(claimedDni).also(entregados::add)

        override fun captureSelfie(): SecureImageBuffer =
            delegate.captureSelfie().also(entregados::add)
    }

    /** Comparte el scheduler de `runTest` para que las latencias simuladas sean virtuales. */
    private fun TestScope.service(
        camera: CameraSource = MockCamera(),
        ocr: OcrEngine = MockOcrEngine(),
        liveness: LivenessDetector = MockLivenessDetector(),
    ) = EdgeIdentityVerificationService(
        camera = camera,
        ocrEngine = ocr,
        livenessDetector = liveness,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    @Test
    fun `captura completa devuelve dni y liveness ok`() = runTest {
        val service = service()

        service.captureDniFront("12345678")
        service.captureSelfie()
        val result = service.verifyAndDiscard()

        assertEquals("12345678", result.dni)
        assertTrue(result.isLive)
    }

    @Test
    fun `sin capturas el resultado es vacio y no vivo`() = runTest {
        val result = service().verifyAndDiscard()

        assertNull(result.dni)
        assertFalse(result.isLive)
    }

    @Test
    fun `falta la selfie y no se corre el pipeline`() = runTest {
        val service = service()

        service.captureDniFront("12345678")
        val result = service.verifyAndDiscard()

        assertNull(result.dni)
        assertFalse(result.isLive)
    }

    @Test
    fun `las imagenes se liberan al verificar`() = runTest {
        val camera = RecordingCameraSource()
        val service = service(camera = camera)

        service.captureDniFront("12345678")
        service.captureSelfie()
        service.verifyAndDiscard()

        assertEquals(2, camera.entregados.size)
        assertTrue(
            "quedaron imágenes vivas después de verificar",
            camera.entregados.all(SecureImageBuffer::isCleared),
        )
    }

    @Test
    fun `las imagenes no sobreviven a la verificacion`() = runTest {
        val service = service()

        service.captureDniFront("12345678")
        service.captureSelfie()
        service.verifyAndDiscard()
        val segundaVerificacion = service.verifyAndDiscard()

        assertNull(segundaVerificacion.dni)
        assertFalse(segundaVerificacion.isLive)
    }

    @Test
    fun `descartar capturas libera las imagenes pendientes`() = runTest {
        val camera = RecordingCameraSource()
        val service = service(camera = camera)

        service.captureDniFront("12345678")
        service.captureSelfie()
        service.discardCaptures()

        assertTrue(camera.entregados.all(SecureImageBuffer::isCleared))
        val result = service.verifyAndDiscard()
        assertNull(result.dni)
        assertFalse(result.isLive)
    }

    @Test
    fun `recapturar libera la imagen anterior`() = runTest {
        val camera = RecordingCameraSource()
        val service = service(camera = camera)

        service.captureDniFront("12345678")
        service.captureDniFront("87654321")

        assertTrue("la primera captura quedó viva", camera.entregados.first().isCleared)
        assertFalse(camera.entregados.last().isCleared)
    }

    @Test
    fun `si el motor de ocr falla igual se liberan las imagenes`() = runTest {
        val camera = RecordingCameraSource()
        val explosivo = object : OcrEngine {
            override fun extractDni(image: ByteArray): String? = error("OCR caído")
        }
        val service = service(camera = camera, ocr = explosivo)

        service.captureDniFront("12345678")
        service.captureSelfie()
        runCatching { service.verifyAndDiscard() }

        assertTrue(camera.entregados.all(SecureImageBuffer::isCleared))
        val result = service.verifyAndDiscard()
        assertNull(result.dni)
        assertFalse(result.isLive)
    }
}
