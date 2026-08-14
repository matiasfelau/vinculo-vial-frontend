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

    /** Comparte el scheduler de `runTest` para que las esperas sean virtuales. */
    private fun TestScope.service(
        ocr: OcrEngine = SimulatedOcrEngine(),
        liveness: LivenessDetector = SimulatedLivenessDetector(),
    ) = EdgeIdentityVerificationService(
        ocrEngine = ocr,
        livenessDetector = liveness,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private suspend fun EdgeIdentityVerificationService.capturarTodo(dni: String = "12345678") {
        acceptDniFront(FakeJpeg.usableDocument(), dni)
        acceptSelfie(FakeJpeg.usableSelfie())
    }

    @Test
    fun `captura completa devuelve dni y liveness ok`() = runTest {
        val service = service()

        service.capturarTodo()
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

        service.acceptDniFront(FakeJpeg.usableDocument(), "12345678")
        val result = service.verifyAndDiscard()

        assertNull(result.dni)
        assertFalse(result.isLive)
    }

    @Test
    fun `una foto de documento inservible se reporta como ilegible`() = runTest {
        val service = service()

        service.acceptDniFront(FakeJpeg.of(width = 320, height = 240), "12345678")
        service.acceptSelfie(FakeJpeg.usableSelfie())
        val result = service.verifyAndDiscard()

        assertNull(result.dni)
        assertFalse(result.isLive)
    }

    @Test
    fun `las imagenes no sobreviven a la verificacion`() = runTest {
        val service = service()

        service.capturarTodo()
        service.verifyAndDiscard()
        val segundaVerificacion = service.verifyAndDiscard()

        assertNull(segundaVerificacion.dni)
        assertFalse(segundaVerificacion.isLive)
    }

    @Test
    fun `los bytes entregados quedan borrados tras verificar`() = runTest {
        val service = service()
        val dniImage = FakeJpeg.usableDocument()
        val selfieImage = FakeJpeg.usableSelfie()

        service.acceptDniFront(dniImage, "12345678")
        service.acceptSelfie(selfieImage)
        service.verifyAndDiscard()

        // El servicio toma posesión de los arrays: al liberarlos los sobreescribe,
        // así que la copia del llamador tampoco conserva la foto.
        assertTrue("la foto del DNI siguió legible", dniImage.all { it == ZERO })
        assertTrue("la selfie siguió legible", selfieImage.all { it == ZERO })
    }

    @Test
    fun `descartar capturas borra las imagenes pendientes`() = runTest {
        val service = service()
        val dniImage = FakeJpeg.usableDocument()

        service.acceptDniFront(dniImage, "12345678")
        service.acceptSelfie(FakeJpeg.usableSelfie())
        service.discardCaptures()

        assertTrue(dniImage.all { it == ZERO })
        val result = service.verifyAndDiscard()
        assertNull(result.dni)
        assertFalse(result.isLive)
    }

    @Test
    fun `recapturar borra la imagen anterior`() = runTest {
        val service = service()
        val primera = FakeJpeg.usableDocument()

        service.acceptDniFront(primera, "12345678")
        service.acceptDniFront(FakeJpeg.usableDocument(), "87654321")

        assertTrue("la primera captura siguió viva", primera.all { it == ZERO })
    }

    @Test
    fun `si el motor de ocr falla igual se borran las imagenes`() = runTest {
        val explosivo = object : OcrEngine {
            override fun extractDni(dniFrontImage: ByteArray, declaredDni: String): String? =
                error("OCR caído")
        }
        val service = service(ocr = explosivo)
        val dniImage = FakeJpeg.usableDocument()

        service.acceptDniFront(dniImage, "12345678")
        service.acceptSelfie(FakeJpeg.usableSelfie())
        runCatching { service.verifyAndDiscard() }

        assertTrue(dniImage.all { it == ZERO })
        val result = service.verifyAndDiscard()
        assertNull(result.dni)
        assertFalse(result.isLive)
    }

    private companion object {
        const val ZERO: Byte = 0
    }
}
