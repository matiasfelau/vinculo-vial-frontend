package com.vinculovial.app.presentation.signup

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vinculovial.app.domain.signup.SignUpError
import com.vinculovial.app.presentation.theme.VinculoVialTheme
import com.vinculovial.app.presentation.theme.VvCream
import com.vinculovial.app.presentation.theme.VvNavy
import com.vinculovial.app.presentation.theme.VvOrange

/**
 * Diálogo único para todos los errores del registro: mensaje genérico y una acción
 * concreta según lo que el usuario pueda corregir.
 */
@Composable
fun SignUpErrorDialog(
    error: SignUpError,
    onDismiss: () -> Unit,
    onEditForm: () -> Unit,
) {
    val presentation = error.presentation()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VvCream,
        icon = {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = VvOrange,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = presentation.title,
                style = MaterialTheme.typography.headlineMedium,
                color = VvNavy,
            )
        },
        text = {
            Text(
                text = presentation.message,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            when (presentation.action) {
                SignUpErrorAction.RETRY_CAPTURE -> TextButton(onClick = onDismiss) {
                    Text("Reintentar", color = VvNavy)
                }

                SignUpErrorAction.EDIT_FORM -> TextButton(onClick = onEditForm) {
                    Text("Revisar mis datos", color = VvNavy)
                }

                SignUpErrorAction.DISMISS -> TextButton(onClick = onDismiss) {
                    Text("Entendido", color = VvNavy)
                }
            }
        },
        dismissButton = if (presentation.action != SignUpErrorAction.EDIT_FORM) {
            null
        } else {
            {
                TextButton(onClick = onDismiss) {
                    Text("Repetir captura", color = VvNavy)
                }
            }
        },
    )
}

@Preview
@Composable
private fun SignUpErrorDialogPreview() {
    VinculoVialTheme {
        SignUpErrorDialog(
            error = SignUpError.DniMismatch,
            onDismiss = {},
            onEditForm = {},
        )
    }
}
