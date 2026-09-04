package ar.felaufernandezbottaro.vinculovial.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvGreen
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange

/**
 * Parada temporal tras el login: todavía no existe una Home/Dashboard con identidad propia
 * (fuera del alcance de US-02/US-03), así que esta sigue siendo la única pantalla autenticada
 * de la app y aloja tanto la entrada a la vinculación de vehículo (US-04) como el logout. El
 * nav host reacciona solo al logout -- ver `VinculoVialNavHost` -- así que esa acción en
 * particular no tiene nada que navegar acá.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onLogout: () -> Unit,
    onLinkVehicle: () -> Unit,
    onScanPlate: () -> Unit,
    onOpenInbox: () -> Unit,
) {
    var showLogoutConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { VvBottomBar() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Hola de nuevo",
                style = MaterialTheme.typography.displaySmall,
                color = VvNavy,
            )
            Text(
                text = "¿Qué querés hacer hoy?",
                style = MaterialTheme.typography.bodyLarge,
                color = VvMuted,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            HomeActionCard(
                icon = Icons.Filled.DirectionsCar,
                title = "Vincular vehículo",
                subtitle = "Sumá un auto a tu cuenta",
                enabled = !state.isLoggingOut,
                onClick = onLinkVehicle,
            )

            HomeActionCard(
                icon = Icons.Filled.CameraAlt,
                title = "Escanear patente",
                subtitle = "Consultá o reportá un vehículo",
                enabled = !state.isLoggingOut,
                onClick = onScanPlate,
            )

            HomeActionCard(
                icon = Icons.Filled.Inbox,
                title = "Mis alertas",
                subtitle = "Revisá los incidentes reportados",
                enabled = !state.isLoggingOut,
                onClick = onOpenInbox,
            )

            Spacer(Modifier.height(8.dp))

            LogoutRow(
                enabled = !state.isLoggingOut,
                loading = state.isLoggingOut,
                onClick = { showLogoutConfirmation = true },
            )
        }
    }

    if (showLogoutConfirmation) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutConfirmation = false
                onLogout()
            },
            onDismiss = { showLogoutConfirmation = false },
        )
    }
}

@Composable
private fun HomeActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = VvMint,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = VvGreen)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.labelLarge, color = VvNavy)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VvMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = VvNavy,
            )
        }
    }
}

/**
 * Deliberadamente discreta -- texto con ícono, no otro botón navy de ancho completo -- para
 * que no compita visualmente con las acciones principales de la pantalla.
 */
@Composable
private fun LogoutRow(enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick, enabled = enabled) {
            if (loading) {
                CircularProgressIndicator(color = VvOrange, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = VvOrange)
            }
            Text(
                text = "Cerrar sesión",
                style = MaterialTheme.typography.labelLarge,
                color = VvOrange,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun LogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Cerrar sesión?") },
        text = { Text("Vas a tener que volver a iniciar sesión para usar la aplicación.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sí, cerrar sesión", color = VvOrange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Seguir", color = VvNavy) }
        },
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    VinculoVialTheme {
        SettingsScreen(
            state = SettingsUiState(),
            onLogout = {},
            onLinkVehicle = {},
            onScanPlate = {},
            onOpenInbox = {},
        )
    }
}
