package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.data.edge.SecureBitmap
import ar.felaufernandezbottaro.vinculovial.data.inbox.mapAlertTypeToDisplay
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceRef
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackRating
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportReason
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvCircleIconButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvInk
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detalle de una alerta del buzón: la evidencia redactada, la valoración (👍/👎) de la validación
 * de IA y el reporte al emisor. La evidencia se baja como bytes y se decodifica en memoria: no
 * hay caché de disco del cliente de imágenes.
 */
@Composable
fun InboxDetailScreen(
    detail: AlertDetailUiState,
    onBack: () -> Unit,
    onRetryEvidence: (EvidenceRef) -> Unit,
    onRate: (FeedbackRating) -> Unit,
    onReportSender: (ReportReason) -> Unit,
) {
    var showReportDialog by remember { mutableStateOf(false) }
    val item = detail.item
    val display = mapAlertTypeToDisplay(item.alertType)

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VvCircleIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    onClick = onBack,
                )
                Text(
                    text = display.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = VvNavy,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        },
        bottomBar = { VvBottomBar() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = item.createdAt.toFriendlyAlertDate(),
                style = MaterialTheme.typography.labelMedium,
                color = VvMuted,
            )

            item.reviewBadgeText()?.let { ReviewBadge(it) }

            EvidenceSection(detail = detail, onRetryEvidence = onRetryEvidence)

            FeedbackSection(detail = detail, onRate = onRate)

            ReportSection(detail = detail, onReportClick = { showReportDialog = true })
        }
    }

    if (showReportDialog) {
        ReportDialog(
            onConfirm = { reason ->
                showReportDialog = false
                onReportSender(reason)
            },
            onDismiss = { showReportDialog = false },
        )
    }
}

@Composable
fun ReviewBadge(text: String) {
    Surface(shape = RoundedCornerShape(50), color = VvOrange.copy(alpha = 0.15f)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = VvOrange,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EvidenceSection(detail: AlertDetailUiState, onRetryEvidence: (EvidenceRef) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = "Evidencia", style = MaterialTheme.typography.titleMedium, color = VvNavy)

        if (detail.evidence.isEmpty()) {
            Text(text = NO_EVIDENCE_MESSAGE, style = MaterialTheme.typography.bodyMedium, color = VvMuted)
        }

        detail.evidence.forEach { slot ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = evidenceRoleLabel(slot.ref.role),
                    style = MaterialTheme.typography.labelMedium,
                    color = VvMuted,
                )
                EvidenceSlotContent(slot = slot, onRetry = { onRetryEvidence(slot.ref) })
            }
        }
    }
}

@Composable
private fun EvidenceSlotContent(slot: EvidenceSlot, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = VvMint, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val load = slot.load) {
                EvidenceLoad.Loading -> CircularProgressIndicator(color = VvNavy, modifier = Modifier.size(28.dp))

                is EvidenceLoad.Loaded -> EvidenceImage(bytes = load.bytes)

                EvidenceLoad.Expired -> Text(
                    text = EVIDENCE_EXPIRED_MESSAGE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VvMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )

                is EvidenceLoad.Failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(8.dp),
                ) {
                    Text(
                        text = load.error.message(),
                        style = MaterialTheme.typography.labelMedium,
                        color = VvOrange,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onRetry) { Text("Reintentar", color = VvNavy) }
                }
            }
        }
    }
}

/** Decodifica los bytes en memoria; el `Bitmap` se sobreescribe al reemplazarse o salir de la pantalla. */
@Composable
private fun EvidenceImage(bytes: ByteArray) {
    val bitmap by produceState<Bitmap?>(initialValue = null, bytes) {
        value = withContext(Dispatchers.Default) { SecureBitmap.decode(bytes, EVIDENCE_DECODE_PX) }
    }
    DisposableEffect(bitmap) {
        val shown = bitmap
        onDispose { SecureBitmap.wipe(shown) }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Foto de la evidencia",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    } ?: CircularProgressIndicator(color = VvNavy, modifier = Modifier.size(28.dp))
}

@Composable
private fun FeedbackSection(detail: AlertDetailUiState, onRate: (FeedbackRating) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "¿Fue correcta la validación de esta alerta?",
            style = MaterialTheme.typography.titleMedium,
            color = VvNavy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RatingButton(
                emoji = "👍",
                description = "Valoración positiva",
                selected = detail.item.myFeedback == FeedbackRating.UP,
                onClick = { onRate(FeedbackRating.UP) },
            )
            RatingButton(
                emoji = "👎",
                description = "Valoración negativa",
                selected = detail.item.myFeedback == FeedbackRating.DOWN,
                onClick = { onRate(FeedbackRating.DOWN) },
            )
        }
        if (detail.feedbackFailed) {
            Text(
                text = "No pudimos guardar tu valoración. Intentá de nuevo.",
                style = MaterialTheme.typography.labelMedium,
                color = VvOrange,
            )
        }
    }
}

@Composable
private fun RatingButton(emoji: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) VvNavy else VvMint,
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = emoji, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ReportSection(detail: AlertDetailUiState, onReportClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val reported = detail.item.reported

        if (reported) {
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text(REPORTED_BUTTON_TEXT)
            }
        } else {
            VvPrimaryButton(
                text = "Reportar emisor",
                onClick = onReportClick,
                loading = detail.report == ReportUiState.Sending,
            )
        }

        (detail.report as? ReportUiState.Failed)?.let {
            Text(text = it.error.message(), style = MaterialTheme.typography.labelMedium, color = VvOrange)
        }
    }
}

@Composable
private fun ReportDialog(onConfirm: (ReportReason) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<ReportReason?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reportar emisor") },
        text = {
            Column {
                ReportReason.entries.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == reason, role = Role.RadioButton, onClick = { selected = reason }),
                    ) {
                        RadioButton(selected = selected == reason, onClick = null)
                        Text(text = reason.label, color = VvInk, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onConfirm) }, enabled = selected != null) {
                Text("Reportar", color = if (selected != null) VvOrange else VvMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = VvNavy) }
        },
    )
}

private const val EVIDENCE_DECODE_PX = 1_600
