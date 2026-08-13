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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
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
 * Captura de las dos imágenes que alimentan el Edge (OCR + Liveness).
 * La captura real de cámara está fuera de alcance de US-01: acá se simula.
 */
@Composable
fun SignUpCaptureScreen(
    state: SignUpUiState,
    onBack: () -> Unit,
    onCaptureDni: () -> Unit,
    onCaptureSelfie: () -> Unit,
    onSubmit: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VvCircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                onClick = onBack,
                enabled = !state.isSubmitting,
            )

            Text(
                text = "Validación de identidad",
                style = MaterialTheme.typography.headlineMedium,
                color = VvNavy,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            CaptureTile(
                title = "Frente del DNI",
                icon = Icons.Filled.Badge,
                captured = state.dniImageCaptured,
                enabled = !state.isSubmitting,
                onClick = onCaptureDni,
            )

            CaptureTile(
                title = "Selfie",
                icon = Icons.Filled.Face,
                captured = state.selfieCaptured,
                enabled = !state.isSubmitting,
                onClick = onCaptureSelfie,
            )

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
                    text = "Las imágenes se analizan en tu dispositivo y se descartan al instante: " +
                        "no se guardan ni viajan a nuestros servidores.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VvMuted,
                )
            }

            Spacer(Modifier.weight(1f))

            VvPrimaryButton(
                text = "Enviar registro",
                onClick = onSubmit,
                enabled = state.canSubmitCapture,
                loading = state.isSubmitting,
            )
        }
    }
}

@Composable
private fun CaptureTile(
    title: String,
    icon: ImageVector,
    captured: Boolean,
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
            .height(96.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = if (captured) Icons.Filled.CheckCircle else icon,
                contentDescription = null,
                tint = if (captured) VvGreen else VvNavy,
                modifier = Modifier.size(34.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = VvNavy,
                )
                Text(
                    text = if (captured) "Imagen lista" else "Tocá para capturar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VvMuted,
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SignUpCapturePreview() {
    VinculoVialTheme {
        SignUpCaptureScreen(
            state = SignUpUiState(dniImageCaptured = true),
            onBack = {},
            onCaptureDni = {},
            onCaptureSelfie = {},
            onSubmit = {},
        )
    }
}
