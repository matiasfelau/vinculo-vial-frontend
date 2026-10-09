package ar.felaufernandezbottaro.vinculovial.presentation.evidence

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Photo Picker del sistema (sin permisos de almacenamiento): sólo existe en el buildType `debug`.
 * Lee la imagen elegida a memoria; nunca la copia a un archivo propio.
 */
class DebugGalleryEvidenceSource(
    private val ioDispatcher: CoroutineDispatcher,
) : GalleryEvidenceSource {

    override val isAvailable: Boolean = true

    @Composable
    override fun rememberPickerLauncher(onPicked: (ByteArray) -> Unit): (() -> Unit)? {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val currentOnPicked by rememberUpdatedState(onPicked)

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val bytes = withContext(ioDispatcher) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } catch (e: IOException) {
                        null
                    }
                }
                if (bytes != null) currentOnPicked(bytes)
            }
        }

        return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}
