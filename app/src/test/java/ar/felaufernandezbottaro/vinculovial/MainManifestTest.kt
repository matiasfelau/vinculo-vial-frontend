package ar.felaufernandezbottaro.vinculovial

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * El manifiesto `main` nunca pide permisos de lectura de medios: la galería de desarrollo usa el
 * Photo Picker del sistema, que no los necesita. (El manifiesto mergeado de release se verifica
 * en `ReleaseNoGalleryTest`.)
 */
class MainManifestTest {

    @Test
    fun `el manifiesto main no declara permisos de lectura de medios`() {
        // El directorio de trabajo de los tests unitarios es el del módulo `app`.
        val manifest = File("src/main/AndroidManifest.xml").readText()

        listOf(
            "READ_MEDIA_IMAGES",
            "READ_MEDIA_VIDEO",
            "READ_MEDIA_VISUAL_USER_SELECTED",
            "READ_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE",
        ).forEach { permission ->
            assertFalse("El manifiesto main declara $permission", manifest.contains(permission))
        }
    }
}
