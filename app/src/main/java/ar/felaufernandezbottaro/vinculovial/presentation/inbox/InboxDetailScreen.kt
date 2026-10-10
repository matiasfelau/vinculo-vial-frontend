package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.felaufernandezbottaro.vinculovial.data.edge.SecureBitmap
import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertConfidence
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceRef
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackRating
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportReason
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvAmber
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvAmberSurface
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvInk
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMintDark
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrangeDark
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvTile
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ConfidencePill
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.DayChip
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ReviewBadge
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviBubble
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviCard
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviChatHeader
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.alertMessage
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.evidenceNote
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.evidenceTileLabel
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * El chat de una alerta, del lado del dueño: ViVIAna avisa que hay un mensaje, lo muestra en
 * una tarjeta con la evidencia redactada y dice cuánto confía en la validación. Al pie, la
 * valoración (👍/👎) y el reporte al emisor. La evidencia se baja como bytes y se decodifica en
 * memoria: no hay caché de disco del cliente de imágenes.
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
    val medium = item.confidence == AlertConfidence.MEDIUM

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ViviChatHeader(onBack = onBack, backDescription = "Volver al buzón")
        HorizontalDivider(color = VvMintDark)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DayChip(item.createdAt.toChatDayStamp(now = remember { Instant.now() }))
            ViviBubble(ViviMessages.OWNER_GREETING)
            MessageCard(detail = detail, medium = medium, onRetryEvidence = onRetryEvidence)
            ConfidenceMessage(confidence = item.confidence)
        }

        HorizontalDivider(color = VvMintDark)
        ChatFooter(
            detail = detail,
            onRate = onRate,
            onReportClick = { showReportDialog = true },
        )
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

/** La tarjeta del mensaje: qué avisó el emisor (siempre una frase fija) y la evidencia redactada. */
@Composable
private fun MessageCard(detail: AlertDetailUiState, medium: Boolean, onRetryEvidence: (EvidenceRef) -> Unit) {
    val item = detail.item

    ViviCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (medium) VvAmber else VvMintDark,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel("Mensaje")
            if (item.requiresReview) ReviewBadge()
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (item.alertType == "CRASHED") Icons.Outlined.Warning else Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = VvNavy,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(22.dp),
            )
            Text(
                text = alertMessage(item.alertType),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
                color = VvNavy,
            )
        }

        SectionLabel("Evidencia", modifier = Modifier.padding(top = 4.dp))

        if (detail.evidence.isEmpty()) {
            Text(text = ViviMessages.EVIDENCE_EMPTY, style = MaterialTheme.typography.bodyMedium, color = VvMuted)
        } else {
            EvidenceGrid(detail = detail, onRetryEvidence = onRetryEvidence)
            if (detail.evidence.any { it.load !is EvidenceLoad.Expired }) {
                Text(
                    text = evidenceNote(item.evidenceExpiresAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = VvMuted,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.2.sp,
        color = VvMuted,
        modifier = modifier,
    )
}

@Composable
private fun EvidenceGrid(detail: AlertDetailUiState, onRetryEvidence: (EvidenceRef) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        detail.evidence.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { slot ->
                    val countInRole = detail.evidence.count { it.ref.role == slot.ref.role }
                    EvidenceTile(
                        slot = slot,
                        label = evidenceTileLabel(slot.ref.role, slot.ref.index, countInRole),
                        onRetry = { onRetryEvidence(slot.ref) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Un casillero de evidencia: la foto redactada si ya llegó; si no, un placeholder con el ícono y
 * la etiqueta, que además dice "La evidencia ya no está disponible" ante un 410.
 */
@Composable
private fun EvidenceTile(slot: EvidenceSlot, label: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = VvTile, modifier = modifier.height(96.dp)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val load = slot.load) {
                EvidenceLoad.Loading -> CircularProgressIndicator(color = VvNavy, modifier = Modifier.size(24.dp))

                is EvidenceLoad.Loaded -> {
                    EvidenceImage(bytes = load.bytes)
                    TileLabelChip(label = label, modifier = Modifier.align(Alignment.BottomStart))
                }

                EvidenceLoad.Expired -> TilePlaceholder(label = label, note = ViviMessages.EVIDENCE_UNAVAILABLE)

                is EvidenceLoad.Failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp),
                ) {
                    Text(
                        text = load.error.message(),
                        style = MaterialTheme.typography.labelMedium,
                        color = VvOrangeDark,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                    )
                    TextButton(onClick = onRetry) { Text("Reintentar", color = VvNavy) }
                }
            }
        }
    }
}

@Composable
private fun TilePlaceholder(label: String, note: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Image, contentDescription = null, tint = VvMuted, modifier = Modifier.size(20.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = VvMuted)
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelMedium,
                color = VvMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun TileLabelChip(label: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(topEnd = 8.dp), color = Color.Black.copy(alpha = 0.55f), modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
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
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    } ?: CircularProgressIndicator(color = VvNavy, modifier = Modifier.size(24.dp))
}

/** Lo que ViVIAna concluye sobre la validación: verde si la confianza es alta, ámbar si es media. */
@Composable
private fun ConfidenceMessage(confidence: AlertConfidence) {
    val medium = confidence == AlertConfidence.MEDIUM

    ViviCard(
        containerColor = if (medium) VvAmberSurface else VvMint,
        borderColor = if (medium) VvAmber else VvMintDark,
    ) {
        ConfidencePill(confidence = confidence)
        Text(text = confidence.message(), style = MaterialTheme.typography.bodyMedium, color = VvInk)
    }
}

@Composable
private fun ChatFooter(detail: AlertDetailUiState, onRate: (FeedbackRating) -> Unit, onReportClick: () -> Unit) {
    val reported = detail.item.reported
    val sending = detail.report == ReportUiState.Sending

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (detail.feedbackFailed) {
            Text(
                text = "No pudimos guardar tu valoración. Intentá de nuevo.",
                style = MaterialTheme.typography.labelMedium,
                color = VvOrangeDark,
            )
        }
        (detail.report as? ReportUiState.Failed)?.let {
            Text(text = it.error.message(), style = MaterialTheme.typography.labelMedium, color = VvOrangeDark)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = ViviMessages.FEEDBACK_PROMPT, style = MaterialTheme.typography.bodyMedium, color = VvInk)
            RatingButton(
                selected = detail.item.myFeedback == FeedbackRating.UP,
                description = "Me sirvió",
                icon = Icons.Outlined.ThumbUp,
                onClick = { onRate(FeedbackRating.UP) },
            )
            RatingButton(
                selected = detail.item.myFeedback == FeedbackRating.DOWN,
                description = "No me sirvió",
                icon = Icons.Outlined.ThumbDown,
                onClick = { onRate(FeedbackRating.DOWN) },
            )

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onReportClick, enabled = !reported && !sending) {
                if (sending) {
                    CircularProgressIndicator(color = VvOrangeDark, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = null,
                        tint = if (reported) VvMuted else VvOrangeDark,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (reported) ViviMessages.REPORTED else ViviMessages.REPORT_SENDER,
                    fontWeight = FontWeight.Bold,
                    color = if (reported) VvMuted else VvOrangeDark,
                )
            }
        }
    }
}

@Composable
private fun RatingButton(
    selected: Boolean,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) VvNavy else Color.White,
        border = BorderStroke(1.5.dp, if (selected) VvNavy else VvMintDark),
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (selected) Color.White else VvNavy,
                modifier = Modifier.size(20.dp),
            )
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
