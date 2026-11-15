package ar.felaufernandezbottaro.vinculovial.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvCircleIconButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvTextField
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/**
 * Pantalla de código de 6 dígitos por email (US-13). La usan la verificación del email en el
 * alta y la recuperación de contraseña, que sólo cambian el título, el texto y qué hacen los
 * botones. Muestra únicamente estado: no sabe de red ni de ViewModels.
 */
@Composable
fun CodeEntryScreen(
    title: String,
    description: String,
    code: String,
    onCodeChange: (String) -> Unit,
    errorText: String?,
    confirmEnabled: Boolean,
    busy: Boolean,
    resendEnabled: Boolean,
    resendLabel: String,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
    backDescription: String,
    confirmLabel: String = "Verificar código",
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { VvBottomBar() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                VvCircleIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backDescription,
                    onClick = onBack,
                    enabled = !busy,
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = VvNavy,
                textAlign = TextAlign.Center,
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = VvMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            VvTextField(
                label = "CÓDIGO",
                value = code,
                onValueChange = onCodeChange,
                placeholder = "123456",
                errorText = errorText,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
            )

            Spacer(Modifier.height(4.dp))

            VvPrimaryButton(
                text = confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled,
                loading = busy,
            )

            TextButton(onClick = onResend, enabled = resendEnabled) {
                Text(text = resendLabel, color = if (resendEnabled) VvNavy else VvMuted)
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CodeEntryScreenPreview() {
    VinculoVialTheme {
        CodeEntryScreen(
            title = "Verificá tu email",
            description = "Te enviamos un código de 6 dígitos.",
            code = "12",
            onCodeChange = {},
            errorText = null,
            confirmEnabled = false,
            busy = false,
            resendEnabled = false,
            resendLabel = CodeFlowTexts.resendIn(42),
            onConfirm = {},
            onResend = {},
            onBack = {},
            backDescription = "Volver al formulario",
        )
    }
}
