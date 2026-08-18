package ar.felaufernandezbottaro.vinculovial.presentation.camera

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange

/**
 * Confirmación antes de abandonar una captura en curso. La comparten las pantallas de
 * cámara: irse a mitad de un escaneo no debería ser un efecto secundario accidental del
 * botón "Atrás" del sistema.
 */
@Composable
internal fun CancelRegistrationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Deseás cancelar el registro?") },
        text = { Text("Vas a perder el progreso de esta captura.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sí, cancelar", color = VvOrange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Seguir", color = VvNavy) }
        },
    )
}
