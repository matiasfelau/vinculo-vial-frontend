package ar.felaufernandezbottaro.vinculovial.presentation.benefits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.felaufernandezbottaro.vinculovial.domain.benefits.Benefit
import ar.felaufernandezbottaro.vinculovial.domain.benefits.BenefitError
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrangeDark
import kotlinx.coroutines.launch

/**
 * El detalle de una promoción: banner, partner, vigencia, descripción y condiciones; sin código,
 * "Obtener código"; con código, el código en grande con "Copiar" y "Abrir". La pantalla
 * solo muestra el estado y emite eventos: copiar y abrir los resuelve quien la compone.
 */
@Composable
fun BenefitDetailScreen(
    state: BenefitsUiState,
    loadBanner: suspend (String) -> ByteArray?,
    onGetCode: () -> Unit,
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val benefit = state.selected
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = BenefitTexts.BACK, tint = VvNavy)
            }

            if (benefit != null) {
                BenefitBanner(
                    benefit = benefit,
                    loadBanner = loadBanner,
                    height = 180.dp,
                    fallbackText = false,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(text = benefit.title, style = MaterialTheme.typography.headlineSmall, color = VvNavy)
                Text(text = benefit.partnerName, style = MaterialTheme.typography.titleMedium, color = VvMuted)
                Text(
                    text = BenefitTexts.validUntil(benefit.endsAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = VvNavy,
                )
                Text(text = benefit.description, style = MaterialTheme.typography.bodyLarge, color = VvNavy)

                TermsSection(terms = benefit.terms)

                ClaimSection(
                    benefit = benefit,
                    claim = state.claim,
                    onGetCode = onGetCode,
                    onCopy = { code ->
                        onCopy(code)
                        scope.launch { snackbarHostState.showSnackbar(BenefitTexts.CODE_COPIED) }
                    },
                    onOpen = onOpen,
                )
            }
        }
    }
}

/** Las condiciones del partner, plegadas hasta que el usuario las abre. */
@Composable
private fun TermsSection(terms: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(12.dp), color = VvMint, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = BenefitTexts.TERMS_TITLE,
                    style = MaterialTheme.typography.labelLarge,
                    color = VvNavy,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = VvNavy,
                )
            }
            if (expanded) {
                Text(
                    text = terms,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VvNavy,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(TERMS_TEXT_TAG),
                )
            }
        }
    }
}

@Composable
private fun ClaimSection(
    benefit: Benefit,
    claim: ClaimState,
    onGetCode: () -> Unit,
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val error = (claim as? ClaimState.Failed)?.error
    val code = benefit.code

    if (error != null) {
        Text(
            text = BenefitTexts.errorMessage(error),
            style = MaterialTheme.typography.bodyMedium,
            color = VvOrangeDark,
            modifier = Modifier.testTag(CLAIM_ERROR_TAG),
        )
    }

    if (code == null) {
        // Con la promoción agotada o vencida no hay nada que reintentar: se ve solo el motivo.
        if (error != BenefitError.NoCodesLeft && error != BenefitError.NotFound) {
            VvPrimaryButton(
                text = BenefitTexts.GET_CODE,
                onClick = onGetCode,
                loading = claim == ClaimState.Claiming,
            )
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VvMint,
        border = BorderStroke(1.5.dp, VvNavy),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            ),
            color = VvNavy,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(vertical = 22.dp, horizontal = 12.dp)
                .testTag(CODE_TAG),
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { onCopy(code) },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.5.dp, VvNavy),
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
        ) {
            Text(BenefitTexts.COPY, color = VvNavy)
        }
        if (benefit.canOpen) {
            VvPrimaryButton(
                text = BenefitTexts.OPEN,
                onClick = { onOpen(benefit.openUrl) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Text(
        text = BenefitTexts.enterCodeIn(benefit.partnerName),
        style = MaterialTheme.typography.bodyMedium,
        color = VvMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

internal const val CODE_TAG = "benefit_code"
internal const val CLAIM_ERROR_TAG = "benefit_claim_error"
internal const val TERMS_TEXT_TAG = "benefit_terms_text"
