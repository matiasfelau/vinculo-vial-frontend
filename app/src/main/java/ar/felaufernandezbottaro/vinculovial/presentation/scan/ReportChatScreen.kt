package ar.felaufernandezbottaro.vinculovial.presentation.scan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvCircleIconButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvInk
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange

/**
 * Reporte del incidente como un hilo de mensajería, no un menú flotante sobre la cámara: la
 * patente ya quedó validada contra `/vehicles/check` en `ScanPlateScreen` -- acá sólo falta
 * elegir el tipo de incidente y publicarlo. El catálogo cerrado se ofrece como respuestas
 * rápidas al pie, nunca como texto libre.
 */
@Composable
fun ReportChatScreen(
    uiState: ScanUiState,
    onAlertTypeSelected: (AlertType) -> Unit,
    onRetry: () -> Unit,
    onFinished: () -> Unit,
    onCancel: () -> Unit,
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = !uiState.isBusy) { showCancelDialog = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                VvCircleIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Cancelar",
                    enabled = !uiState.isBusy,
                    onClick = { showCancelDialog = true },
                )
            }
        },
        bottomBar = { VvBottomBar() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val messages = remember(uiState) { chatMessages(uiState) }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages) { message -> ChatBubble(message) }

                if (uiState.status is ScanStatus.SendingAlert) {
                    item { TypingBubble() }
                }
            }

            ReportChatFooter(
                status = uiState.status,
                onAlertTypeSelected = onAlertTypeSelected,
                onRetry = onRetry,
                onFinished = onFinished,
            )
        }
    }

    if (showCancelDialog) {
        CancelReportDialog(
            onConfirm = {
                showCancelDialog = false
                onCancel()
            },
            onDismiss = { showCancelDialog = false },
        )
    }
}

@Composable
private fun ReportChatFooter(
    status: ScanStatus,
    onAlertTypeSelected: (AlertType) -> Unit,
    onRetry: () -> Unit,
    onFinished: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        when (status) {
            ScanStatus.AwaitingAlertType -> QuickReplyRow(onSelected = onAlertTypeSelected)
            is ScanStatus.AlertFailed -> {
                Text(
                    text = status.error.message(),
                    style = MaterialTheme.typography.labelMedium,
                    color = VvOrange,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                VvPrimaryButton(text = "Reintentar", onClick = onRetry)
            }
            ScanStatus.AlertSent -> VvPrimaryButton(text = "Continuar", onClick = onFinished)
            else -> Unit
        }
    }
}

/** Catálogo cerrado como respuestas rápidas: nunca texto libre del usuario. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickReplyRow(onSelected: (AlertType) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AlertType.entries.forEach { alertType ->
            Surface(
                onClick = { onSelected(alertType) },
                shape = RoundedCornerShape(50),
                color = VvMint,
            ) {
                Text(
                    text = alertType.value,
                    style = MaterialTheme.typography.labelLarge,
                    color = VvNavy,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private sealed interface ChatMessage {
    data class System(val text: String) : ChatMessage
    data class User(val text: String) : ChatMessage
}

private fun chatMessages(uiState: ScanUiState): List<ChatMessage> = buildList {
    add(ChatMessage.System("Vehículo identificado. ¿Qué infracción deseas reportar?"))

    uiState.selectedAlertType?.let { add(ChatMessage.User(it.value)) }

    if (uiState.status is ScanStatus.AlertSent) {
        add(ChatMessage.System("Reporte enviado de forma anónima."))
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    when (message) {
        is ChatMessage.System -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = VvMint,
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VvInk,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        is ChatMessage.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = VvNavy,
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Burbuja del sistema mientras se publica el reporte -- simula "escribiendo…". */
@Composable
private fun TypingBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = VvMint,
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                CircularProgressIndicator(color = VvNavy, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CancelReportDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Deseás cancelar el reporte?") },
        text = { Text("La patente ya quedó verificada, pero no se va a enviar ningún reporte.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sí, cancelar", color = VvOrange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Seguir", color = VvNavy) }
        },
    )
}
