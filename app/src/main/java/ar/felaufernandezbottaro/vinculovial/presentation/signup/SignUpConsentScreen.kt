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
    "A continuación se te va a solicitar que tomes dos imágenes, una de tu rostro y otra del " +
        "frente de tu DNI. El objetivo de esto es asegurar que los datos ingresados coinciden " +
        "con tu documentación y así ayudar a mantener la seguridad de la aplicación.\n\n" +
        "Estos datos serán utilizados únicamente para esta validación: el análisis ocurre en " +
        "tu propio dispositivo y las imágenes se eliminan de forma permanente apenas termina, " +
        "sin copias en disco ni envío a nuestros servidores.\n\n" +
        "Al iniciar el proceso de validación, usted está aceptando compartir sus datos con nosotros."

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
