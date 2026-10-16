package ar.felaufernandezbottaro.vinculovial.presentation.sent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlert
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.toChatDayStamp
import ar.felaufernandezbottaro.vinculovial.presentation.scan.SenderChatRow
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMintDark
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.DayChip
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.LockedFootnote
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviChatHeader
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages
import java.time.Instant

/**
 * El chat del emisor de una alerta que ya envió, de sólo lectura: se arma desde el estado
 * ([sentChatItems]) y no tiene acciones. Nunca muestra fotos, veredicto, motivo ni datos del dueño.
 */
@Composable
fun SentChatScreen(alert: SentAlert, onBack: () -> Unit) {
    val now = remember { Instant.now() }
    val items = remember(alert, now) { sentChatItems(alert, now = now) }

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
            DayChip(alert.createdAt.toChatDayStamp(now = now))
            items.forEach { item -> SenderChatRow(item) }
        }

        HorizontalDivider(color = VvMintDark)
        LockedFootnote(
            text = ViviMessages.SENDER_FIXED_NOTE,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}
