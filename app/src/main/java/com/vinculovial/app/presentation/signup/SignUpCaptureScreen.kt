package com.vinculovial.app.presentation.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vinculovial.app.presentation.components.VvBottomBar
import com.vinculovial.app.presentation.components.VvCircleIconButton
import com.vinculovial.app.presentation.components.VvPrimaryButton
import com.vinculovial.app.presentation.theme.VinculoVialTheme
import com.vinculovial.app.presentation.theme.VvGreen
import com.vinculovial.app.presentation.theme.VvMint
import com.vinculovial.app.presentation.theme.VvMuted
import com.vinculovial.app.presentation.theme.VvNavy

/**
 * Las tres capturas del onboarding biométrico. Todo el análisis ocurre después, en el
 * dispositivo, al tocar "Verificar y registrarme".
 */
@Composable
fun SignUpCaptureScreen(
    state: SignUpUiState,
    onBack: () -> Unit,
    onCapture: (CaptureTarget) -> Unit,
    onSubmit: () -> Unit,
    onErrorDismissed: () -> Unit = {},
    onEditForm: () -> Unit = {},
) {
    state.error?.let { error ->
        SignUpErrorDialog(
            error = error,
            onDismiss = onErrorDismissed,
            onEditForm = onEditForm,
        )
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            VvCircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                onClick = onBack,
                enabled = !state.isBusy,
            )

            Text(
                text = "Validación de identidad",
                style = MaterialTheme.typography.headlineMedium,
                color = VvNavy,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            CaptureTarget.entries.forEach { target ->
                CaptureTile(
                    target = target,
                    captured = state.isCaptured(target),
                    capturing = state.capturing == target,
                    enabled = !state.isBusy,
                    onClick = { onCapture(target) },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = VvGreen,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Comparamos tu cara con la foto del documento acá mismo, en tu " +
                        "teléfono. Las fotos se borran al terminar: nunca salen del dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VvMuted,
                )
            }

            Spacer(Modifier.height(8.dp))

            VvPrimaryButton(
                text = "Verificar y registrarme",
                onClick = onSubmit,
                enabled = state.canSubmitCapture,
                loading = state.isSubmitting,
            )
        }
    }
}

@Composable
private fun CaptureTile(
    target: CaptureTarget,
    captured: Boolean,
    capturing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = VvMint,
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (capturing) {
                CircularProgressIndicator(
                    color = VvNavy,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Icon(
                    imageVector = if (captured) Icons.Filled.CheckCircle else target.icon(),
                    contentDescription = null,
                    tint = if (captured) VvGreen else VvNavy,
                    modifier = Modifier.size(32.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = target.title(),
                    style = MaterialTheme.typography.labelLarge,
                    color = VvNavy,
                )
                Text(
                    text = when {
                        capturing -> "Procesando…"
                        captured -> "Listo"
                        else -> target.hint()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = VvMuted,
                )
            }
        }
    }
}

private fun CaptureTarget.icon(): ImageVector = when (this) {
    CaptureTarget.DNI_BACK -> Icons.Filled.CreditCard
    CaptureTarget.DNI_FRONT -> Icons.Filled.Badge
    CaptureTarget.SELFIE -> Icons.Filled.Face
}

private fun CaptureTarget.title(): String = when (this) {
    CaptureTarget.DNI_BACK -> "Reverso del DNI"
    CaptureTarget.DNI_FRONT -> "Frente del DNI"
    CaptureTarget.SELFIE -> "Selfie"
}

private fun CaptureTarget.hint(): String = when (this) {
    CaptureTarget.DNI_BACK -> "Tocá para escanear el código de barras"
    CaptureTarget.DNI_FRONT -> "Tocá para fotografiar tu foto del documento"
    CaptureTarget.SELFIE -> "Tocá y seguí la indicación en pantalla"
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SignUpCapturePreview() {
    VinculoVialTheme {
        SignUpCaptureScreen(
            state = SignUpUiState(completedCaptures = setOf(CaptureTarget.DNI_BACK)),
            onBack = {},
            onCapture = {},
            onSubmit = {},
        )
    }
}
