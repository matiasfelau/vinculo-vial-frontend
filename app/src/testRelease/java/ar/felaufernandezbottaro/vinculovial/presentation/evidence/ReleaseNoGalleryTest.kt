package ar.felaufernandezbottaro.vinculovial.presentation.evidence

import ar.felaufernandezbottaro.vinculovial.di.GalleryEvidenceModule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

/**
 * Variante `release`: no existe ninguna forma de adjuntar desde la galería. El código del
 * picker ni siquiera se compila (vive en `src/debug`), y el manifiesto mergeado no pide
 * permisos de lectura de medios.
 */
class ReleaseNoGalleryTest {

    @Test
    fun `en release la fuente de galeria es la no-op y no esta disponible`() {
        val source = GalleryEvidenceModule.provideGalleryEvidenceSource()

        assertSame(NoOpGalleryEvidenceSource, source)
        assertFalse(source.isAvailable)
    }

    @Test
    fun `el codigo del picker no existe en release`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("ar.felaufernandezbottaro.vinculovial.presentation.evidence.DebugGalleryEvidenceSource")
        }
    }

    @Test
    fun `el manifiesto mergeado de release no pide permisos de lectura de medios`() {
        val path = checkNotNull(System.getProperty("mergedManifestPath")) {
            "Falta mergedManifestPath: lo define app/build.gradle.kts para testReleaseUnitTest"
        }
        val manifest = File(path).readText()

        FORBIDDEN_PERMISSIONS.forEach { permission ->
            assertFalse("El manifiesto de release declara $permission", manifest.contains(permission))
        }
    }

    private companion object {
        val FORBIDDEN_PERMISSIONS = listOf(
            "READ_MEDIA_IMAGES",
            "READ_MEDIA_VIDEO",
            "READ_MEDIA_VISUAL_USER_SELECTED",
            "READ_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE",
        )
    }
}
