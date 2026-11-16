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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.material.icons.filled.Fingerprint
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.session.SessionTexts
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrangeDark

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
    onOpenVehicles: () -> Unit,
    onScanPlate: () -> Unit,
    onOpenInbox: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onBiometricOfferDismissed: () -> Unit,
    onBiometricActivationFailureDismissed: () -> Unit,
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
                text = "¡Hola de nuevo! 👋",
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
                title = "Mis vehículos",
                subtitle = "Vinculá o desvinculá tus autos",
                enabled = !state.isLoggingOut,
                onClick = onOpenVehicles,
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

            if (state.isBiometricAvailable) {
                BiometricRow(
                    checked = state.isBiometricEnabled,
                    enabled = !state.isLoggingOut,
                    onCheckedChange = onBiometricToggle,
                )
            }

            LogoutRow(
                enabled = !state.isLoggingOut,
                loading = state.isLoggingOut,
                onClick = { showLogoutConfirmation = true },
            )

            DeleteAccountRow(enabled = !state.isLoggingOut, onClick = onDeleteAccount)
        }
    }

    if (state.showBiometricOffer) {
        BiometricOfferDialog(
            onAccept = { onBiometricToggle(true) },
            onDismiss = onBiometricOfferDismissed,
        )
    }

    if (state.biometricActivationFailed) {
        AlertDialog(
            onDismissRequest = onBiometricActivationFailureDismissed,
            text = { Text(SessionTexts.ACTIVATION_FAILED) },
            confirmButton = {
                TextButton(onClick = onBiometricActivationFailureDismissed) {
                    Text(SessionTexts.ACTIVATION_FAILED_DISMISS, color = VvNavy)
                }
            },
        )
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
                    Icon(imageVector = icon, contentDescription = null, tint = VvNavy)
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

/** El interruptor del desbloqueo con huella (US-12): activarlo envuelve el refresh actual; apagarlo borra blob y clave. */
@Composable
private fun BiometricRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(imageVector = Icons.Filled.Fingerprint, contentDescription = null, tint = VvNavy)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = SessionTexts.SETTING_TITLE, style = MaterialTheme.typography.labelLarge, color = VvNavy)
            Text(text = SessionTexts.SETTING_SUBTITLE, style = MaterialTheme.typography.bodyMedium, color = VvMuted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Se ofrece una vez por login con contraseña, cuando hay biometría fuerte enrolada y no está activada. */
@Composable
private fun BiometricOfferDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SessionTexts.OFFER_TITLE) },
        text = { Text(SessionTexts.OFFER_TEXT) },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(SessionTexts.OFFER_ACCEPT, color = VvNavy) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(SessionTexts.OFFER_DISMISS, color = VvMuted) }
        },
    )
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

/**
 * Más discreta todavía que [LogoutRow] y apartada de ella: es una acción sin vuelta atrás, no
 * debe quedar al alcance de un toque distraído. La confirmación real es la pantalla siguiente.
 */
@Composable
private fun DeleteAccountRow(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick, enabled = enabled) {
            Icon(imageVector = Icons.Filled.DeleteForever, contentDescription = null, tint = VvOrangeDark)
            Text(
                text = "Eliminar cuenta",
                style = MaterialTheme.typography.labelLarge,
                color = VvOrangeDark,
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
            onOpenVehicles = {},
            onScanPlate = {},
            onOpenInbox = {},
            onDeleteAccount = {},
            onBiometricToggle = {},
            onBiometricOfferDismissed = {},
            onBiometricActivationFailureDismissed = {},
        )
    }
}
