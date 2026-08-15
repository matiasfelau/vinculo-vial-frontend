package com.vinculovial.app.presentation.camera

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.vinculovial.app.presentation.theme.VvNavy
import com.vinculovial.app.presentation.theme.VvOrange

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
