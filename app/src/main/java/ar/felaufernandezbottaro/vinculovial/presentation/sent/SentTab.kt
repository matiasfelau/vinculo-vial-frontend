package ar.felaufernandezbottaro.vinculovial.presentation.sent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.data.inbox.mapAlertTypeToDisplay
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlert
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.InboxFullScreenError
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.InboxLoading
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.LoadMoreEffect
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.message
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.pagingFooter
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.toImageVector
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.toInboxListTime
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvInk
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.SentStatePill
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.alertTitle
import java.time.Instant

/**
 * La pestaña "Enviados": las alertas que envió el usuario, una fila por alerta, con paginación
 * infinita. Cada fila muestra sólo el ícono del tipo, su título, la fecha y la pill de estado.
 */
@Composable
fun SentTab(
    state: SentUiState,
    onRetry: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetryNextPage: () -> Unit,
    onOpenSent: (String) -> Unit,
) {
    when (state) {
        SentUiState.Loading -> InboxLoading()
        SentUiState.Empty -> SentEmptyState()
        is SentUiState.Error -> InboxFullScreenError(message = state.error.message(), onRetry = onRetry)
        is SentUiState.Content -> SentList(
            items = state.items,
            isLoadingMore = state.isLoadingMore,
            onLoadNextPage = onLoadNextPage,
            onOpenSent = onOpenSent,
        )
        is SentUiState.ContentWithPageError -> SentList(
            items = state.items,
            pageErrorMessage = state.error.message(),
            onRetryPage = onRetryNextPage,
            onOpenSent = onOpenSent,
        )
    }
}

@Composable
private fun SentList(
    items: List<SentAlert>,
    isLoadingMore: Boolean = false,
    pageErrorMessage: String? = null,
    onRetryPage: (() -> Unit)? = null,
    onLoadNextPage: (() -> Unit)? = null,
    onOpenSent: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    // Un solo "ahora" por composición: todas las filas comparan contra el mismo instante.
    val now = remember(items) { Instant.now() }

    if (onLoadNextPage != null) LoadMoreEffect(listState, onLoadNextPage)

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { alert ->
            SentRow(alert = alert, now = now, onClick = { onOpenSent(alert.id) })
        }

        pagingFooter(isLoadingMore, pageErrorMessage, onRetryPage)
    }
}

/** Una alerta enviada como fila: ícono del tipo, título, fecha y pill de estado. */
@Composable
private fun SentRow(alert: SentAlert, now: Instant, onClick: () -> Unit) {
    Column {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = VvMint, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = mapAlertTypeToDisplay(alert.alertType).icon.toImageVector(),
                            contentDescription = null,
                            tint = VvNavy,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = alertTitle(alert.alertType),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = VvInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = alert.createdAt.toInboxListTime(now = now),
                            style = MaterialTheme.typography.labelMedium,
                            color = VvMuted,
                        )
                    }
                    SentStatePill(state = alert.state)
                }
            }
        }
        HorizontalDivider(color = VvMint)
    }
}

@Composable
private fun SentEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = VvMint,
            border = BorderStroke(2.dp, VvNavy),
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, tint = VvNavy, modifier = Modifier.size(44.dp))
            }
        }
        Text(
            text = ViviMessages.SENT_EMPTY_TITLE,
            style = MaterialTheme.typography.titleMedium,
            color = VvNavy,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = ViviMessages.SENT_EMPTY_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = VvMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
