package ar.felaufernandezbottaro.vinculovial.presentation.vivi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.R
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlertState
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvAmber
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvAmberDark
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvAmberSurface
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvGreen
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvGreenDark
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvInk
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMintDark
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrangeDark
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvTile

/**
 * El avatar de ViVIAna. Siempre sale de `R.drawable.vivi_avatar`, un recurso único: cuando haya
 * logo se reemplaza ese archivo y todas las pantallas lo toman solo.
 */
@Composable
fun ViviAvatar(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.vivi_avatar),
        contentDescription = "Logo de ${ViviMessages.NAME} (provisorio)",
        modifier = modifier.size(size),
    )
}

/** Encabezado de una conversación: volver, avatar y "ViVIAna / Asistente de Vínculo Vial". */
@Composable
fun ViviChatHeader(onBack: () -> Unit, backDescription: String, backEnabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            onClick = onBack,
            enabled = backEnabled,
            shape = CircleShape,
            color = VvMint,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backDescription,
                    tint = VvNavy,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        ViviAvatar(size = 40.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = ViviMessages.NAME,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
                color = VvInk,
            )
            Text(text = ViviMessages.TAGLINE, style = MaterialTheme.typography.labelMedium, color = VvMuted)
        }
    }
}

private val ViviBubbleShape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
private val UserBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

/**
 * Un mensaje de ViVIAna. [isWarning] lo pinta ámbar: un aviso que pide cuidado o un error.
 * [time] es una marca de hora opcional bajo el texto ("Hoy 10:42").
 */
@Composable
fun ViviBubble(text: String, isWarning: Boolean = false, time: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = ViviBubbleShape,
            color = if (isWarning) VvAmberSurface else VvMint,
            border = if (isWarning) BorderStroke(1.dp, VvAmber) else null,
            modifier = Modifier.widthIn(max = 290.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = text, style = MaterialTheme.typography.bodyMedium, color = VvInk)
                if (time != null) {
                    Text(text = time, style = MaterialTheme.typography.labelMedium, color = VvMuted)
                }
            }
        }
    }
}

/** Un mensaje del usuario. */
@Composable
fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(shape = UserBubbleShape, color = VvNavy, modifier = Modifier.widthIn(max = 290.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** Una burbuja de ViVIAna con contenido propio (la tarjeta del mensaje, la confianza). */
@Composable
fun ViviCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    borderColor: Color = VvMintDark,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = ViviBubbleShape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = modifier.widthIn(max = 310.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

/** El sello de día que abre una conversación ("Hoy 10:42"). */
@Composable
fun DayChip(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp), color = VvMint) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = VvMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** "Ver fotos": la pill de cada fila de Recibidos, que invita a abrir el mensaje. */
@Composable
fun ViewPhotosPill(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = VvMint,
        border = BorderStroke(1.dp, VvNavy),
        modifier = modifier.height(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = VvNavy,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = ViviMessages.INBOX_VIEW_PHOTOS,
                style = MaterialTheme.typography.labelMedium,
                color = VvNavy,
            )
        }
    }
}

/** La pill de estado de una fila de Enviados: el color dice cómo va la alerta, el texto lo confirma. */
@Composable
fun SentStatePill(state: SentAlertState, modifier: Modifier = Modifier) {
    val (container, border, content) = when (state) {
        SentAlertState.IN_VALIDATION -> Triple(VvAmberSurface, VvAmber, VvAmberDark)
        SentAlertState.VALIDATED -> Triple(VvMint, VvGreen, VvGreenDark)
        SentAlertState.SEEN -> Triple(Color.White, VvNavy, VvNavy)
        SentAlertState.REJECTED -> Triple(Color.White, VvOrangeDark, VvOrangeDark)
        SentAlertState.NO_RESPONSE -> Triple(VvTile, VvMuted, VvMuted)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = BorderStroke(1.dp, border),
        modifier = modifier.height(24.dp),
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = state.label(), style = MaterialTheme.typography.labelMedium, color = content)
        }
    }
}

/** El punto naranja de "no leído". */
@Composable
fun UnreadDot(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = VvOrange,
        modifier = modifier
            .size(10.dp)
            .semantics { contentDescription = "No leído" },
    ) {}
}

/** Una nota con candado al pie de una pantalla (privacidad del emisor). */
@Composable
fun LockedFootnote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = VvMuted,
            modifier = Modifier.size(14.dp),
        )
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = VvMuted)
    }
}
