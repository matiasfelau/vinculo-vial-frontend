package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvGreen
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvInk
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMintDark
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrangeDark
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.LockedFootnote
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.UnreadDot
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViewPhotosPill
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviAvatar
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.alertTitle
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.inboxPreview
import java.time.Instant

/** Las pestañas del buzón. */
enum class InboxTab(val title: String) {
    RECEIVED("Recibidos"),
    SENT("Enviados"),
}

/** Cuántas de las alertas cargadas todavía no se abrieron en este dispositivo. */
fun unreadCount(items: List<AlertItem>, readIds: Set<String>): Int = items.count { it.id !in readIds }

/**
 * El buzón como lista de conversaciones con ViVIAna. "Recibidos" son las alertas sobre los
 * vehículos del usuario, una fila por alerta; tocar una abre su chat. "Enviados" todavía es una
 * pantalla en construcción, sin datos.
 */
@Composable
fun InboxScreen(
    uiState: InboxUiState,
    readIds: Set<String>,
    onRetry: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetryNextPage: () -> Unit,
    onOpenAlert: (String) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(InboxTab.RECEIVED) }

    val loadedItems = when (uiState) {
        is InboxUiState.Content -> uiState.items
        is InboxUiState.ContentWithPageError -> uiState.items
        else -> emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        InboxHeader(
            selectedTab = selectedTab,
            unread = unreadCount(loadedItems, readIds),
            onSelectTab = { selectedTab = it },
        )

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                InboxTab.SENT -> InboxSentUnderConstruction()
                InboxTab.RECEIVED -> when (uiState) {
                    InboxUiState.Loading -> InboxLoading()
                    InboxUiState.Empty -> InboxEmptyState()
                    is InboxUiState.Content -> InboxContentList(
                        items = uiState.items,
                        readIds = readIds,
                        isLoadingMore = uiState.isLoadingMore,
                        onLoadNextPage = onLoadNextPage,
                        onOpenAlert = onOpenAlert,
                    )
                    is InboxUiState.Error -> InboxFullScreenError(message = uiState.error.message(), onRetry = onRetry)
                    is InboxUiState.ContentWithPageError -> InboxContentList(
                        items = uiState.items,
                        readIds = readIds,
                        pageErrorMessage = uiState.error.message(),
                        onRetryPage = onRetryNextPage,
                        onLoadNextPage = null,
                        onOpenAlert = onOpenAlert,
                    )
                }
            }
        }

        LockedFootnote(
            text = ViviMessages.INBOX_FOOTNOTE,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun InboxHeader(selectedTab: InboxTab, unread: Int, onSelectTab: (InboxTab) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Buzón",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
            color = VvNavy,
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            InboxTab.entries.forEach { tab ->
                InboxTabCell(
                    tab = tab,
                    selected = tab == selectedTab,
                    badge = if (tab == InboxTab.RECEIVED) unread else 0,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider(color = VvMintDark)
    }
}

@Composable
private fun InboxTabCell(
    tab: InboxTab,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.height(45.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) VvNavy else VvMuted,
                )
                if (badge > 0) {
                    Surface(shape = RoundedCornerShape(10.dp), color = VvOrange) {
                        Text(
                            text = badge.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = VvInk,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 20.dp)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(if (selected) VvNavy else MaterialTheme.colorScheme.background),
            )
        }
    }
}

@Composable
private fun InboxLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = VvNavy)
    }
}

/** "Enviados" todavía no existe: sin datos locales ni pedidos, sólo el aviso. */
@Composable
private fun InboxSentUnderConstruction() {
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
                Icon(Icons.Outlined.Construction, contentDescription = null, tint = VvNavy, modifier = Modifier.size(44.dp))
            }
        }
        Text(
            text = ViviMessages.SENT_UNDER_CONSTRUCTION_TITLE,
            style = MaterialTheme.typography.titleMedium,
            color = VvNavy,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = ViviMessages.SENT_UNDER_CONSTRUCTION_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = VvMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Ilustración propia, deliberadamente distinta de [InboxFullScreenError]: un aro verde (el
 * mismo tono del isotipo, [VvGreen]) en vez del naranja de error -- "sin alertas" es el
 * resultado deseable, no una falla, y el color no debe sugerir lo contrario.
 */
@Composable
private fun InboxEmptyState() {
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
            border = BorderStroke(2.dp, VvGreen),
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Inbox, contentDescription = null, tint = VvGreen, modifier = Modifier.size(44.dp))
            }
        }
        Text(
            text = "Todo tranquilo por acá",
            style = MaterialTheme.typography.titleMedium,
            color = VvNavy,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = "Todavía nadie reportó un incidente sobre tus vehículos vinculados. " +
                "Te vamos a avisar acá apenas llegue una alerta nueva.",
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

/** Cuántos ítems antes del final dispara la carga de la página siguiente. */
private const val LOAD_MORE_THRESHOLD = 3

@Composable
private fun InboxContentList(
    items: List<AlertItem>,
    readIds: Set<String>,
    isLoadingMore: Boolean = false,
    pageErrorMessage: String? = null,
    onRetryPage: (() -> Unit)? = null,
    onLoadNextPage: (() -> Unit)? = null,
    onOpenAlert: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    // Un solo "ahora" por composición: todas las filas comparan contra el mismo instante.
    val now = remember(items) { Instant.now() }

    if (onLoadNextPage != null) {
        val shouldLoadMore by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                lastVisible.index >= layoutInfo.totalItemsCount - 1 - LOAD_MORE_THRESHOLD
            }
        }
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) onLoadNextPage()
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { item ->
            InboxChatRow(item = item, unread = item.id !in readIds, now = now, onClick = { onOpenAlert(item.id) })
        }

        if (isLoadingMore) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VvNavy, modifier = Modifier.size(24.dp))
                }
            }
        }

        if (pageErrorMessage != null && onRetryPage != null) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
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

/**
 * Una alerta como fila de chat: avatar de ViVIAna, título (el mensaje del tipo), hora, la última
 * línea de ViVIAna y la confianza. Si todavía no se abrió, va en negrita, con la hora y un punto
 * naranja resaltados.
 */
@Composable
private fun InboxChatRow(item: AlertItem, unread: Boolean, now: Instant, onClick: () -> Unit) {
    Column {
        Surface(
            onClick = onClick,
            color = if (unread) Color.White else MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ViviAvatar(size = 44.dp, modifier = Modifier.clip(CircleShape))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = alertTitle(item.alertType),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                            color = VvInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = item.createdAt.toInboxListTime(now = now),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                            color = if (unread) VvOrangeDark else VvMuted,
                        )
                    }
                    Text(
                        text = item.inboxPreview(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unread) VvInk else VvMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ViewPhotosPill()
                        if (unread) UnreadDot()
                    }
                }
            }
        }
        HorizontalDivider(color = VvMint)
    }
}
