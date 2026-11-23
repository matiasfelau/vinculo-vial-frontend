package ar.felaufernandezbottaro.vinculovial.presentation.terms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.domain.terms.TermsBlock
import ar.felaufernandezbottaro.vinculovial.domain.terms.TermsSpan
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvInk
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMintDark
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/** La pantalla "Términos y Condiciones" (`Route.TERMS`): pública, sin sesión y sin red. */
@Composable
fun TermsScreen(
    state: TermsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = TermsTexts.BACK, tint = VvNavy)
                    }
                    Text(
                        text = TermsTexts.SCREEN_TITLE,
                        style = MaterialTheme.typography.titleMedium,
                        color = VvNavy,
                    )
                }
                HorizontalDivider(color = VvMintDark)
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (state) {
                TermsUiState.Loading -> CircularProgressIndicator(color = VvNavy)

                TermsUiState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = TermsTexts.LOAD_FAILED, style = MaterialTheme.typography.bodyMedium, color = VvMuted)
                    TextButton(onClick = onRetry) { Text(TermsTexts.RETRY, color = VvNavy) }
                }

                is TermsUiState.Ready -> TermsDocument(state.blocks)
            }
        }
    }
}

/** Dibuja los bloques del documento: títulos, párrafos, listas con viñetas y tablas. */
@Composable
fun TermsDocument(blocks: List<TermsBlock>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(DOCUMENT_TAG),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(blocks) { _, block -> TermsBlockView(block) }
    }
}

@Composable
private fun TermsBlockView(block: TermsBlock) {
    when (block) {
        is TermsBlock.Heading -> Text(
            text = block.text,
            style = if (block.level <= 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VvNavy,
            modifier = Modifier.padding(top = if (block.level <= 1) 0.dp else 8.dp),
        )

        is TermsBlock.Paragraph -> Text(
            text = block.spans.toAnnotated(),
            style = MaterialTheme.typography.bodyMedium,
            color = VvInk,
        )

        is TermsBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "•", style = MaterialTheme.typography.bodyMedium, color = VvNavy)
                    Text(
                        text = item.toAnnotated(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = VvInk,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Tres columnas no entran en un teléfono: cada fila se dibuja como una tarjeta con sus
        // títulos de columna, sin perder ninguna celda.
        is TermsBlock.Table -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.rows.forEach { row ->
                Surface(shape = RoundedCornerShape(12.dp), color = VvMint, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEachIndexed { index, cell ->
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = VvNavy)) {
                                        append(block.headers.getOrElse(index) { "" })
                                        append(": ")
                                    }
                                    append(cell.toAnnotated())
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = VvInk,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun List<TermsSpan>.toAnnotated(): AnnotatedString = buildAnnotatedString {
    forEach { span ->
        if (span.bold) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
        } else {
            append(span.text)
        }
    }
}

internal const val DOCUMENT_TAG = "terms_document"
