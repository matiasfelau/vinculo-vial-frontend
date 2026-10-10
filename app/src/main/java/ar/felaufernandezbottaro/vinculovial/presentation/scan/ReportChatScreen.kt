package ar.felaufernandezbottaro.vinculovial.presentation.scan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.toChatDayStamp
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMintDark
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.DayChip
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.UserBubble
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviBubble
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviChatHeader
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.senderMessage
import java.time.Instant

/**
 * El chat del emisor con ViVIAna. La patente ya quedó validada contra `/alerts/attempts` en
 * `ScanPlateScreen`: acá sólo falta elegir qué avisar, sacar las fotos de evidencia y enviarlo.
 * El emisor nunca escribe: elige entre mensajes fijos, para que el dueño no pueda identificarlo.
 */
@Composable
fun ReportChatScreen(
    uiState: ScanUiState,
    onAlertTypeSelected: (AlertType) -> Unit,
    onReportRequested: () -> Unit,
    onRetry: () -> Unit,
    onRestart: () -> Unit,
    onFinished: () -> Unit,
    onCancel: () -> Unit,
) {
    var showCancelDialog by remember { mutableStateOf(false) }
    val startedAt = remember { Instant.now() }

    BackHandler(enabled = !uiState.isBusy) { showCancelDialog = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ViviChatHeader(
            onBack = { showCancelDialog = true },
            backDescription = "Cancelar",
            backEnabled = !uiState.isBusy,
        )
        HorizontalDivider(color = VvMintDark)

        val items = remember(uiState) { senderChatItems(uiState) }
        val listState = rememberLazyListState()

        // La conversación crece hacia abajo: al sumarse un mensaje se muestra el último.
        LaunchedEffect(items.size) {
            if (items.isNotEmpty()) listState.animateScrollToItem(items.size)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { DayChip(startedAt.toChatDayStamp()) }
            items(items) { item -> SenderChatRow(item) }
        }

        HorizontalDivider(color = VvMintDark)
        SenderChatFooter(
            uiState = uiState,
            onAlertTypeSelected = onAlertTypeSelected,
            onReportRequested = onReportRequested,
            onRetry = onRetry,
            onRestart = onRestart,
            onFinished = onFinished,
            onClose = onCancel,
        )
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
private fun SenderChatRow(item: SenderChatItem) {
    when (item) {
        is SenderChatItem.Vivi -> ViviBubble(text = item.text, isWarning = item.isWarning)
        is SenderChatItem.User -> UserBubble(text = item.text)
        is SenderChatItem.Attachment -> AttachmentBubble(item)
        SenderChatItem.Typing -> TypingBubble()
    }
}

@Composable
private fun SenderChatFooter(
    uiState: ScanUiState,
    onAlertTypeSelected: (AlertType) -> Unit,
    onReportRequested: () -> Unit,
    onRetry: () -> Unit,
    onRestart: () -> Unit,
    onFinished: () -> Unit,
    onClose: () -> Unit,
) {
    val status = uiState.status

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            status is ScanStatus.AlertSent -> VvPrimaryButton(text = "Cerrar", onClick = onFinished)

            status is ScanStatus.AlertFailed -> when (status.error.recovery()) {
                SendAlertRecovery.RETRY -> VvPrimaryButton(text = "Reintentar", onClick = onRetry)
                SendAlertRecovery.RESTART -> VvPrimaryButton(text = "Volver a empezar", onClick = onRestart)
                SendAlertRecovery.CLOSE -> VvPrimaryButton(text = "Cerrar", onClick = onClose)
            }

            status is ScanStatus.AwaitingAlertType && uiState.selectedAlertType == null ->
                MessageOptions(onAlertTypeSelected = onAlertTypeSelected)

            status is ScanStatus.AwaitingAlertType && uiState.evidence.phase == EvidencePhase.COMPLETE ->
                VvPrimaryButton(text = "Enviar", onClick = onReportRequested)

            else -> Unit
        }
    }
}

/** Los mensajes fijos entre los que el emisor elige: sólo los tipos con validación de IA. */
@Composable
private fun MessageOptions(onAlertTypeSelected: (AlertType) -> Unit) {
    Text(
        text = ViviMessages.SENDER_OPTIONS_HEADER.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = VvMuted,
    )

    AlertType.offered.forEach { alertType ->
        OutlinedButton(
            onClick = { onAlertTypeSelected(alertType) },
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, VvNavy),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp),
        ) {
            Icon(
                imageVector = optionIcon(alertType),
                contentDescription = null,
                tint = VvNavy,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = alertType.senderMessage(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = VvNavy,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = VvMuted, modifier = Modifier.size(14.dp))
        Text(
            text = ViviMessages.SENDER_FIXED_NOTE,
            style = MaterialTheme.typography.labelMedium,
            color = VvMuted,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun optionIcon(alertType: AlertType): ImageVector = when (alertType) {
    AlertType.CRASHED -> Icons.Outlined.Warning
    else -> Icons.Outlined.DirectionsCar
}

/** Las fotos como adjunto del usuario: una etiqueta por foto, sin mostrar la imagen. */
@Composable
private fun AttachmentBubble(item: SenderChatItem.Attachment) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = VvNavy,
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.labels.chunked(ATTACHMENT_COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { label -> AttachmentTile(label) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        if (item.sent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = VvMuted, modifier = Modifier.size(14.dp))
                Text(
                    text = "Enviado",
                    style = MaterialTheme.typography.labelMedium,
                    color = VvMuted,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AttachmentTile(label: String) {
    Column(modifier = Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF34508F), modifier = Modifier.size(58.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
    }
}

/** ViVIAna "escribiendo" mientras se publica el envío. */
@Composable
private fun TypingBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, VvMintDark),
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

private const val ATTACHMENT_COLUMNS = 4
