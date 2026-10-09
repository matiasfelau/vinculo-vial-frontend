package ar.felaufernandezbottaro.vinculovial.presentation.evidence

import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.edge.SecureImageBuffer
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceEncryptor
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceImageProcessor
import ar.felaufernandezbottaro.vinculovial.data.evidence.EvidenceProcessingResult
import ar.felaufernandezbottaro.vinculovial.data.evidence.SyntheticRasterFactory
import ar.felaufernandezbottaro.vinculovial.data.evidence.hasExif
import ar.felaufernandezbottaro.vinculovial.data.evidence.syntheticJpeg
import ar.felaufernandezbottaro.vinculovial.di.GalleryEvidenceModule
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.Base64

/** Variante `debug`: la galería (Photo Picker) está disponible y entra al mismo pipeline que la cámara. */
@OptIn(ExperimentalCoroutinesApi::class)
class DebugGalleryEvidenceTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun registerTink() {
            HybridConfig.register()
        }
    }

    private val dispatcher = UnconfinedTestDispatcher()

    private val iaKeyset = KeysetHandle.generateNew(
        KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
    )
    private val iaPublicKey = Base64.getEncoder().encodeToString(
        (iaKeyset.publicKeysetHandle.primary.key as HpkePublicKey).publicKeyBytes.toByteArray(),
    )

    private val attemptId = "3f2b8c1e-0000-4000-8000-000000000001"

    @Test
    fun `en debug la fuente de galeria es la real y esta disponible`() {
        val source = GalleryEvidenceModule.provideGalleryEvidenceSource(dispatcher)

        assertTrue(source is DebugGalleryEvidenceSource)
        assertTrue(source.isAvailable)
    }

    @Test
    fun `una imagen de galeria con EXIF sale sin EXIF y cifrada igual que una de camara`() = runTest {
        val processor = EvidenceImageProcessor(SyntheticRasterFactory(), dispatcher)
        val encryptor = EvidenceEncryptor(HpkeEncryptor(), iaPublicKey)

        val fromGallery = processor.process(syntheticJpeg(2_400, 1_800, withExif = true), rotationDegrees = 0).photoBuffer()
        val fromCamera = processor.process(syntheticJpeg(2_400, 1_800), rotationDegrees = 0).photoBuffer()

        assertFalse(fromGallery.read { hasExif(it) })

        val sealedGallery = encryptor.encrypt(fromGallery, attemptId)
        val sealedCamera = encryptor.encrypt(fromCamera, attemptId)
        assertArrayEquals(decryptAsIa(sealedCamera), decryptAsIa(sealedGallery))
        assertFalse(hasExif(decryptAsIa(sealedGallery)))
    }

    private fun EvidenceProcessingResult.photoBuffer(): SecureImageBuffer {
        assertTrue("esperaba Processed pero fue $this", this is EvidenceProcessingResult.Processed)
        return (this as EvidenceProcessingResult.Processed).photo
    }

    private fun decryptAsIa(sealed: ByteArray): ByteArray =
        iaKeyset.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
            .decrypt(sealed, attemptId.toByteArray(Charsets.UTF_8))
}
