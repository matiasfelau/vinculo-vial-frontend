package ar.felaufernandezbottaro.vinculovial.presentation.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/**
 * Desbloqueo (US-12): la sesión está bloqueada y sólo existe el refresh token envuelto. El botón
 * de huella abre el prompt biométrico; "Usar contraseña" lleva a Login sin tocar el blob (se borra
 * recién cuando ese login sale bien). Sin red, la pantalla se queda acá y deja reintentar.
 */
@Composable
fun UnlockScreen(
    state: UnlockUiState,
    onUnlock: () -> Unit,
    onUsePassword: () -> Unit,
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
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = Icons.Filled.Fingerprint,
                contentDescription = null,
                tint = VvNavy,
                modifier = Modifier.height(64.dp),
            )
            Text(
                text = SessionTexts.UNLOCK_TITLE,
                style = MaterialTheme.typography.displaySmall,
                color = VvNavy,
                textAlign = TextAlign.Center,
            )
            Text(
                text = SessionTexts.UNLOCK_SUBTITLE,
                style = MaterialTheme.typography.bodyLarge,
                color = VvMuted,
                textAlign = TextAlign.Center,
            )

            state.failure?.let { failure ->
                Text(
                    text = failure.message(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))

            VvPrimaryButton(
                text = if (state.failure == UnlockFailure.NETWORK) {
                    SessionTexts.UNLOCK_RETRY_BUTTON
                } else {
                    SessionTexts.UNLOCK_BUTTON
                },
                onClick = onUnlock,
                enabled = !state.isBusy,
                loading = state.isBusy,
            )

            TextButton(onClick = onUsePassword, enabled = state.status !is UnlockStatus.Refreshing) {
                Text(
                    text = SessionTexts.USE_PASSWORD_BUTTON,
                    style = MaterialTheme.typography.labelLarge,
                    color = VvNavy,
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun UnlockScreenPreview() {
    VinculoVialTheme {
        UnlockScreen(
            state = UnlockUiState(status = UnlockStatus.Failed(UnlockFailure.NETWORK)),
            onUnlock = {},
            onUsePassword = {},
        )
    }
}
