package ar.felaufernandezbottaro.vinculovial.presentation.scan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
 * patente ya quedó validada contra `/alerts/attempts` en `ScanPlateScreen` -- acá sólo falta
 * elegir el tipo de incidente, sacar las fotos de evidencia y publicarlo. El catálogo cerrado
 * se ofrece como respuestas rápidas al pie, nunca como texto libre.
 */
@Composable
fun ReportChatScreen(
    uiState: ScanUiState,
    onAlertTypeSelected: (AlertType) -> Unit,
    onReportRequested: () -> Unit,
    onRetry: () -> Unit,
    onRestart: () -> Unit,
    onOpenInbox: () -> Unit,
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
                    .statusBarsPadding()
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
                evidencePhase = uiState.evidence.phase,
                onAlertTypeSelected = onAlertTypeSelected,
                onReportRequested = onReportRequested,
                onRetry = onRetry,
                onRestart = onRestart,
                onOpenInbox = onOpenInbox,
                onFinished = onFinished,
                onClose = onCancel,
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
    evidencePhase: EvidencePhase,
    onAlertTypeSelected: (AlertType) -> Unit,
    onReportRequested: () -> Unit,
    onRetry: () -> Unit,
    onRestart: () -> Unit,
    onOpenInbox: () -> Unit,
    onFinished: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        when (status) {
            ScanStatus.AwaitingAlertType ->
                if (evidencePhase == EvidencePhase.COMPLETE) {
                    VvPrimaryButton(text = "Reportar", onClick = onReportRequested)
                } else {
                    AlertTypeComposer(onStartEvidence = onAlertTypeSelected)
                }
            is ScanStatus.AlertFailed -> {
                Text(
                    text = status.error.message(),
                    style = MaterialTheme.typography.labelMedium,
                    color = VvOrange,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                when (status.error.recovery()) {
                    SendAlertRecovery.RETRY -> VvPrimaryButton(text = "Reintentar", onClick = onRetry)
                    SendAlertRecovery.RESTART -> VvPrimaryButton(text = "Volver a empezar", onClick = onRestart)
                    SendAlertRecovery.CHECK_INBOX -> {
                        VvPrimaryButton(text = "Ir al buzón", onClick = onOpenInbox)
                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) { Text("Cerrar") }
                    }
                    SendAlertRecovery.CLOSE -> VvPrimaryButton(text = "Cerrar", onClick = onClose)
                }
            }
            ScanStatus.AlertSent -> VvPrimaryButton(text = "Continuar", onClick = onFinished)
            else -> Unit
        }
    }
}

/**
 * Selección del tipo de incidente, tal como lo pide el wireframe: un dropdown con el catálogo
 * cerrado (nunca texto libre). Sólo ofrece los tipos que tienen validación de IA; elegir uno
 * arranca la captura guiada de la evidencia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertTypeComposer(onStartEvidence: (AlertType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AlertType?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected?.label.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Tipo de alerta") },
                placeholder = { Text("Elegí qué querés reportar") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AlertType.offered.forEach { alertType ->
                    DropdownMenuItem(
                        text = { Text(alertType.label) },
                        onClick = {
                            selected = alertType
                            expanded = false
                        },
                    )
                }
            }
        }

        VvPrimaryButton(
            text = "Sacar fotos",
            onClick = { selected?.let(onStartEvidence) },
            enabled = selected != null,
        )
    }
}

private sealed interface ChatMessage {
    data class System(val text: String) : ChatMessage
    data class User(val text: String) : ChatMessage
}

private fun chatMessages(uiState: ScanUiState): List<ChatMessage> = buildList {
    add(ChatMessage.System("Vehículo identificado. ¿Qué infracción deseas reportar?"))

    uiState.selectedAlertType?.let { add(ChatMessage.User(it.label)) }

    if (uiState.evidence.phase == EvidencePhase.COMPLETE) {
        add(ChatMessage.System("Fotos listas. Ya podés enviar el reporte."))
    }

    if (uiState.status is ScanStatus.AlertSent) {
        add(ChatMessage.System("Alerta enviada. La estamos validando."))
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
