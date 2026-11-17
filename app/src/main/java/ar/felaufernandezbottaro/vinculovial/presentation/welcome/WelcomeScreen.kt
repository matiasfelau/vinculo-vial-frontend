package ar.felaufernandezbottaro.vinculovial.presentation.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvWordmark
import ar.felaufernandezbottaro.vinculovial.presentation.terms.TermsLinkText
import ar.felaufernandezbottaro.vinculovial.presentation.terms.TermsTexts
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/**
 * Homepage previa al login: la puerta de entrada real de la app, antes inexistente (Splash
 * caía directo en Login). Ofrece iniciar sesión como acción principal y crear cuenta como
 * alternativa menos visible, tal como pide el wireframe.
 */
@Composable
fun WelcomeScreen(
    onGoToSignIn: () -> Unit,
    onGoToSignUp: () -> Unit,
    onOpenTerms: () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { VvBottomBar() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            VvWordmark()

            Text(
                text = "Reportá y enterate de infracciones sobre los vehículos que vinculaste, " +
                    "de forma anónima.",
                style = MaterialTheme.typography.bodyLarge,
                color = VvMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 40.dp),
            )

            VvPrimaryButton(text = "Iniciar sesión", onClick = onGoToSignIn)

            Spacer(Modifier.height(20.dp))

            SignUpLink(onGoToSignUp = onGoToSignUp)

            Spacer(Modifier.height(24.dp))

            TermsLinkText(
                before = "",
                link = TermsTexts.LINK,
                onOpenTerms = onOpenTerms,
                style = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Alternativa deliberadamente menos visible que el botón principal, tal como pide el wireframe. */
@Composable
private fun SignUpLink(onGoToSignUp: () -> Unit) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = VvMuted)) { append("¿Todavía no tenés una cuenta? ") }
        withStyle(SpanStyle(color = VvNavy, textDecoration = TextDecoration.Underline)) {
            append("Creá una aquí")
        }
    }
    ClickableText(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        onClick = { onGoToSignUp() },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun WelcomeScreenPreview() {
    VinculoVialTheme {
        WelcomeScreen(onGoToSignIn = {}, onGoToSignUp = {})
    }
}
