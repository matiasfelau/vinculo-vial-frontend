package ar.felaufernandezbottaro.vinculovial.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/**
 * Parada temporal tras el login: todavía no existe una Home/Dashboard (fuera del alcance de
 * US-02/US-03), así que esta es la única pantalla autenticada de la app y la que aloja la
 * acción de Logout. El nav host reacciona solo al logout -- ver `VinculoVialNavHost` -- así
 * que acá no hay nada que navegar.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onLogout: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { VvBottomBar() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Sesión iniciada",
                style = MaterialTheme.typography.displaySmall,
                color = VvNavy,
                textAlign = TextAlign.Center,
            )

            VvPrimaryButton(
                text = "Cerrar sesión",
                onClick = onLogout,
                enabled = !state.isLoggingOut,
                loading = state.isLoggingOut,
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    VinculoVialTheme {
        SettingsScreen(state = SettingsUiState(), onLogout = {})
    }
}
