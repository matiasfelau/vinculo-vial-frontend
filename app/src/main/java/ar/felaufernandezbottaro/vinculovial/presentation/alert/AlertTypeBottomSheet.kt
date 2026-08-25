package ar.felaufernandezbottaro.vinculovial.presentation.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/**
 * Catálogo cerrado de incidentes (US-05): el usuario elige uno de [AlertType], nunca texto
 * libre. Se abre una sola vez que `/vehicles/check` confirmó que el vehículo está en la red.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertTypeBottomSheet(
    onAlertTypeSelected: (AlertType) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "¿Qué querés reportar?",
                style = MaterialTheme.typography.headlineSmall,
                color = VvNavy,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            AlertType.entries.forEach { alertType ->
                AlertTypeOption(alertType = alertType, onClick = { onAlertTypeSelected(alertType) })
            }
        }
    }
}

@Composable
private fun AlertTypeOption(alertType: AlertType, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = VvMint,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = alertType.value,
            style = MaterialTheme.typography.titleMedium,
            color = VvNavy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 16.dp),
        )
    }
}
