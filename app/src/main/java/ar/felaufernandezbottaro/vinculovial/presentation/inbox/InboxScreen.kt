package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.data.inbox.mapAlertTypeToDisplay
import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvCircleIconButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvInk
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange

/**
 * Lista de solo lectura de los incidentes reportados sobre los vehículos del usuario. Sin
 * acciones sobre los ítems: no hay marcar como leída, resolver ni borrar -- ver CLAUDE.md.
 */
@Composable
fun InboxScreen(
    uiState: InboxUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VvCircleIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    onClick = onBack,
                )
                Text(
                    text = "Mis alertas",
                    style = MaterialTheme.typography.titleLarge,
                    color = VvNavy,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        },
        bottomBar = { VvBottomBar() },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (uiState) {
                InboxUiState.Loading -> InboxLoading()
                InboxUiState.Empty -> InboxEmptyState()
                is InboxUiState.Content -> InboxContentList(items = uiState.items, isLoadingMore = uiState.isLoadingMore)
                is InboxUiState.Error -> InboxFullScreenError(message = uiState.error.message(), onRetry = onRetry)
                is InboxUiState.ContentWithPageError ->
                    InboxContentList(items = uiState.items, pageErrorMessage = uiState.error.message(), onRetryPage = onRetry)
            }
        }
    }
}

@Composable
private fun InboxLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = VvNavy)
    }
}

@Composable
private fun InboxEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = VvMint, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Inbox, contentDescription = null, tint = VvNavy, modifier = Modifier.size(36.dp))
            }
        }
        Text(
            text = "No tenés alertas",
            style = MaterialTheme.typography.titleMedium,
            color = VvNavy,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Todavía no reportaron ningún incidente sobre tus vehículos.",
            style = MaterialTheme.typography.bodyMedium,
            color = VvMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun InboxFullScreenError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = VvOrange,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        VvPrimaryButton(text = "Reintentar", onClick = onRetry)
    }
}

@Composable
private fun InboxContentList(
    items: List<AlertItem>,
    isLoadingMore: Boolean = false,
    pageErrorMessage: String? = null,
    onRetryPage: (() -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item -> AlertItemRow(item) }

        if (isLoadingMore) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VvNavy, modifier = Modifier.size(24.dp))
                }
            }
        }

        if (pageErrorMessage != null && onRetryPage != null) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = pageErrorMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = VvOrange,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    VvPrimaryButton(text = "Reintentar", onClick = onRetryPage)
                }
            }
        }
    }
}

@Composable
private fun AlertItemRow(item: AlertItem) {
    val display = mapAlertTypeToDisplay(item.alertType)

    Surface(shape = RoundedCornerShape(14.dp), color = VvMint) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(display.icon.toImageVector(), contentDescription = null, tint = VvNavy)
                }
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(text = display.label, style = MaterialTheme.typography.bodyLarge, color = VvInk)
                Text(
                    text = item.createdAt.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = VvMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
