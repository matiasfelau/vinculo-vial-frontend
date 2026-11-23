package ar.felaufernandezbottaro.vinculovial.presentation.terms

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/** Los textos de la pantalla de Términos y de los lugares desde donde se llega a ella (US-19). */
object TermsTexts {
    const val SCREEN_TITLE = "Términos y Condiciones"

    /** El nombre del enlace, tal como se lo ve en la Bienvenida y en Settings. */
    const val LINK = "Términos y Condiciones y Política de Privacidad"

    const val CHECKBOX_BEFORE = "Leí y acepto los "
    const val CHECKBOX_LINK = "Términos y Condiciones y la Política de Privacidad"
    const val CHECKBOX = CHECKBOX_BEFORE + CHECKBOX_LINK

    const val FEEDBACK_BEFORE =
        "Al enviar tu valoración, aceptás que las fotos redactadas se usen para mejorar la validación automática. "
    const val FEEDBACK_LINK = "Ver Términos"
    const val FEEDBACK = FEEDBACK_BEFORE + FEEDBACK_LINK

    const val BACK = "Volver"
    const val LOAD_FAILED = "No pudimos mostrar los términos. Intentá de nuevo."
    const val RETRY = "Reintentar"
}

/**
 * Un texto con un tramo que lleva a los Términos. El tramo es un enlace de toque, y además todo el
 * texto expone la acción a los lectores de pantalla (que no distinguen tramos).
 */
@Composable
fun TermsLinkText(
    before: String,
    link: String,
    onOpenTerms: () -> Unit,
    modifier: Modifier = Modifier,
    after: String = "",
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = VvMuted)) { append(before) }
        withLink(
            LinkAnnotation.Clickable(
                tag = "terms",
                styles = TextLinkStyles(SpanStyle(color = VvNavy, textDecoration = TextDecoration.Underline)),
                linkInteractionListener = { onOpenTerms() },
            ),
        ) { append(link) }
        withStyle(SpanStyle(color = VvMuted)) { append(after) }
    }
    Text(
        text = text,
        style = style,
        modifier = modifier.semantics {
            onClick(label = "Abrir los Términos y Condiciones") {
                onOpenTerms()
                true
            }
        },
    )
}
