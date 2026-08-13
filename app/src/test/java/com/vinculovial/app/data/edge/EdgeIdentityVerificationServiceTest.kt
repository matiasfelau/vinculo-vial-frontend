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

    /** Comparte el scheduler de `runTest` para que las latencias simuladas sean virtuales. */
    private fun TestScope.service(
        ocr: OcrEngine = MockOcrEngine(),
        liveness: LivenessDetector = MockLivenessDetector(),
    ) = EdgeIdentityVerificationService(
        camera = MockCamera(),
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
    fun `descartar capturas invalida la sesion`() = runTest {
        val service = service()

        service.captureDniFront("12345678")
        service.captureSelfie()
        service.discardCaptures()
        val result = service.verifyAndDiscard()

        assertNull(result.dni)
        assertFalse(result.isLive)
    }

    @Test
    fun `si el motor de ocr falla igual se descartan las imagenes`() = runTest {
        val explosivo = object : OcrEngine {
            override fun extractDni(image: ByteArray): String? = error("OCR caído")
        }
        val service = service(ocr = explosivo)

        service.captureDniFront("12345678")
        service.captureSelfie()
        runCatching { service.verifyAndDiscard() }

        val result = service.verifyAndDiscard()
        assertNull(result.dni)
        assertFalse(result.isLive)
    }
}
