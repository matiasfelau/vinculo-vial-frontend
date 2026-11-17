package ar.felaufernandezbottaro.vinculovial.data.edge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentIdentity
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.EdgeResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * La selfie del alta: se comprime para entrar en el payload y el servicio Edge sólo la retiene
 * hasta verificarla (Zero Retention): después de `verifyAndDiscard` no queda nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelfiePipelineTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val document = DocumentIdentity(number = "12345678", sex = "M", procedureNumber = "00307513148")

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }

    private fun service() = EdgeIdentityVerificationService(SelfieCompressor(), dispatcher)

    // -- SelfieCompressor ---------------------------------------------------------------------

    @Test
    fun `una selfie grande se achica a 800 px como maximo`() {
        val base64 = SelfieCompressor().compress(jpeg(1600, 1200))

        assertNotNull(base64)
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        assertTrue("el lado mayor debe ser <= 800, fue ${maxOf(bounds.outWidth, bounds.outHeight)}", maxOf(bounds.outWidth, bounds.outHeight) <= 800)
    }

    @Test
    fun `una selfie chica no se agranda`() {
        val base64 = SelfieCompressor().compress(jpeg(320, 240))

        assertNotNull(base64)
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= 320)
    }

    @Test
    fun `sin documento ni selfie la captura esta incompleta`() = runTest {
        val result = service().verifyAndDiscard()

        assertEquals(EdgeResult.Rejected(EdgeResult.Reason.INCOMPLETE_CAPTURE), result)
    }

    @Test
    fun `con documento pero sin selfie la captura esta incompleta`() = runTest {
        val service = service()
        service.acceptDocument(document)

        assertEquals(EdgeResult.Rejected(EdgeResult.Reason.INCOMPLETE_CAPTURE), service.verifyAndDiscard())
    }

    @Test
    fun `con selfie pero sin documento la captura esta incompleta`() = runTest {
        val service = service()
        service.acceptSelfie(jpeg(100, 100), livenessPassed = true)

        assertEquals(EdgeResult.Rejected(EdgeResult.Reason.INCOMPLETE_CAPTURE), service.verifyAndDiscard())
    }

    @Test
    fun `sin superar la prueba de vida no se manda nada`() = runTest {
        val service = service()
        service.acceptDocument(document)
        service.acceptSelfie(jpeg(100, 100), livenessPassed = false)

        assertEquals(EdgeResult.Rejected(EdgeResult.Reason.LIVENESS_FAILED), service.verifyAndDiscard())
    }

    @Test
    fun `con todo en orden entrega el documento y la selfie comprimida`() = runTest {
        val service = service()
        service.acceptDocument(document)
        service.acceptSelfie(jpeg(640, 480), livenessPassed = true)

        val result = service.verifyAndDiscard()

        assertTrue("fue $result", result is EdgeResult.Verified)
        result as EdgeResult.Verified
        assertEquals(document, result.document)
        assertTrue(result.selfieBase64.isNotEmpty())
    }

    @Test
    fun `despues de verificar no queda nada retenido`() = runTest {
        val service = service()
        service.acceptDocument(document)
        service.acceptSelfie(jpeg(640, 480), livenessPassed = true)
        service.verifyAndDiscard()

        assertEquals(EdgeResult.Rejected(EdgeResult.Reason.INCOMPLETE_CAPTURE), service.verifyAndDiscard())
    }

    @Test
    fun `descartar las capturas borra el documento y la selfie`() = runTest {
        val service = service()
        service.acceptDocument(document)
        service.acceptSelfie(jpeg(640, 480), livenessPassed = true)

        service.discardCaptures()

        assertEquals(EdgeResult.Rejected(EdgeResult.Reason.INCOMPLETE_CAPTURE), service.verifyAndDiscard())
    }

    @Test
    fun `una segunda selfie reemplaza a la primera`() = runTest {
        val service = service()
        service.acceptDocument(document)
        service.acceptSelfie(jpeg(640, 480), livenessPassed = false)
        service.acceptSelfie(jpeg(640, 480), livenessPassed = true)

        assertTrue(service.verifyAndDiscard() is EdgeResult.Verified)
    }
}
