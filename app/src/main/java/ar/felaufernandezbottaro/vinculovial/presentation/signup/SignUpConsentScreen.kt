package ar.felaufernandezbottaro.vinculovial.presentation.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvCircleIconButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme

private const val CONSENT_TEXT =
    "A continuación, te vamos a pedir que escanees el código de barras de tu DNI y, después, " +
        "te tomes una selfie. Ésto nos ayuda a mantener la seguridad de todos al verificar que " +
        "quienes se registran son personas reales. Tus datos solo serán utilizados en este " +
        "momento, enviándose al Renaper para que éste los valide. Después de verificar tu " +
        "identidad, exceptuando tu número de DNI, destruiremos de nuestros servidores el resto " +
        "de los datos obtenidos."

/** Pantalla de consentimiento previa a la captura (segunda pantalla del wireframe). */
@Composable
fun SignUpConsentScreen(
    onBack: () -> Unit,
    onStartValidation: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { VvBottomBar() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            VvCircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver al formulario",
                onClick = onBack,
            )

            Spacer(Modifier.weight(0.08f))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                VvCircleIconButton(
                    icon = Icons.Filled.PhotoCamera,
                    contentDescription = "Validación con cámara",
                    onClick = onStartValidation,
                    size = 56,
                )
            }

            Spacer(Modifier.weight(0.08f))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = CONSENT_TEXT,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Justify,
                )
            }

            Spacer(Modifier.weight(0.08f))

            VvPrimaryButton(
                text = "Iniciar Validación",
                onClick = onStartValidation,
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SignUpConsentPreview() {
    VinculoVialTheme {
        SignUpConsentScreen(onBack = {}, onStartValidation = {})
    }
}
