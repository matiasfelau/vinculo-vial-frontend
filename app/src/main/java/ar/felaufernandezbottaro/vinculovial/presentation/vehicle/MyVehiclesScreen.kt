package ar.felaufernandezbottaro.vinculovial.presentation.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.VehicleCount
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvCircleIconButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvTextField
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvCream
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/**
 * "Mis vehículos" (US-14): cuántos vehículos tiene vinculados el usuario, nunca cuáles. Sólo
 * muestra estado y emite eventos.
 */
@Composable
fun MyVehiclesScreen(
    state: VehiclesUiState,
    onBack: () -> Unit,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    onUnlinkAll: () -> Unit,
    onRetry: () -> Unit,
    onPlateChange: (String) -> Unit,
    onPlateContinue: () -> Unit,
    onPlateBack: () -> Unit,
    onPlateConfirm: () -> Unit,
    onUnlinkAllConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
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
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                VvCircleIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    onClick = onBack,
                )
            }

            Text(
                text = VehicleTexts.TITLE,
                style = MaterialTheme.typography.displaySmall,
                color = VvNavy,
            )

            val count = state.count
            when {
                count != null -> Content(count, state, onLink, onUnlink, onUnlinkAll)
                state.loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = VvNavy,
                )
                else -> LoadFailure(error = state.loadError, onRetry = onRetry)
            }
        }
    }

    when (val dialog = state.dialog) {
        VehicleDialog.None -> Unit
        is VehicleDialog.Plate -> PlateDialog(
            dialog = dialog,
            max = state.count?.max ?: 0,
            busy = state.busy,
            onPlateChange = onPlateChange,
            onContinue = onPlateContinue,
            onBack = onPlateBack,
            onConfirm = onPlateConfirm,
            onDismiss = onDialogDismiss,
        )

        is VehicleDialog.UnlinkAll -> UnlinkAllDialog(
            dialog = dialog,
            max = state.count?.max ?: 0,
            busy = state.busy,
            onConfirm = onUnlinkAllConfirm,
            onDismiss = onDialogDismiss,
        )
    }
}

@Composable
private fun Content(
    count: VehicleCount,
    state: VehiclesUiState,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    onUnlinkAll: () -> Unit,
) {
    Text(
        text = VehicleTexts.summary(count),
        style = MaterialTheme.typography.bodyLarge,
        color = VvNavy,
    )

    VvPrimaryButton(
        text = VehicleTexts.LINK_BUTTON,
        onClick = onLink,
        enabled = state.canLink,
    )
    if (count.isFull) {
        Text(
            text = VehicleTexts.limit(count.max),
            style = MaterialTheme.typography.bodyMedium,
            color = VvMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    OutlinedButton(onClick = onUnlink, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        Text(VehicleTexts.UNLINK_BUTTON, color = VvNavy)
    }
    OutlinedButton(onClick = onUnlinkAll, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        Text(VehicleTexts.UNLINK_ALL_BUTTON, color = VvNavy)
    }
}

@Composable
private fun LoadFailure(error: VehicleError?, onRetry: () -> Unit) {
    Text(
        text = (error ?: VehicleError.Unexpected).message(max = 0),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
    VvPrimaryButton(text = VehicleTexts.RETRY, onClick = onRetry)
}

/** Modal de alta o de baja: escribir la patente y confirmarla. */
@Composable
private fun PlateDialog(
    dialog: VehicleDialog.Plate,
    max: Int,
    busy: Boolean,
    onPlateChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val linking = dialog.action == PlateAction.LINK
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VvCream,
        title = {
            Text(
                text = if (linking) VehicleTexts.LINK_TITLE else VehicleTexts.UNLINK_TITLE,
                style = MaterialTheme.typography.headlineMedium,
                color = VvNavy,
            )
        },
        text = {
            when (dialog.step) {
                PlateStep.ENTRY -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VvTextField(
                        label = VehicleTexts.PLATE_LABEL,
                        value = dialog.input,
                        onValueChange = onPlateChange,
                        placeholder = VehicleTexts.PLATE_PLACEHOLDER,
                        errorText = dialog.problem?.message() ?: dialog.error?.message(max),
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }

                PlateStep.CONFIRM -> Text(
                    text = if (linking) {
                        VehicleTexts.linkConfirmation(dialog.input)
                    } else {
                        VehicleTexts.unlinkConfirmation(dialog.input)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        confirmButton = {
            when (dialog.step) {
                PlateStep.ENTRY -> TextButton(onClick = onContinue, enabled = !busy) {
                    Text(VehicleTexts.CONTINUE, color = VvNavy)
                }

                PlateStep.CONFIRM -> TextButton(onClick = onConfirm, enabled = !busy) {
                    Text(VehicleTexts.CONFIRM, color = VvNavy)
                }
            }
        },
        dismissButton = {
            when (dialog.step) {
                PlateStep.ENTRY -> TextButton(onClick = onDismiss, enabled = !busy) {
                    Text(VehicleTexts.CANCEL, color = VvMuted)
                }

                PlateStep.CONFIRM -> TextButton(onClick = onBack, enabled = !busy) {
                    Text(VehicleTexts.BACK, color = VvMuted)
                }
            }
        },
    )
}

@Composable
private fun UnlinkAllDialog(
    dialog: VehicleDialog.UnlinkAll,
    max: Int,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VvCream,
        title = {
            Text(
                text = VehicleTexts.UNLINK_ALL_TITLE,
                style = MaterialTheme.typography.headlineMedium,
                color = VvNavy,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = VehicleTexts.UNLINK_ALL_CONFIRMATION, style = MaterialTheme.typography.bodyLarge)
                dialog.error?.let {
                    Text(
                        text = it.message(max),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) { Text(VehicleTexts.CONFIRM, color = VvNavy) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(VehicleTexts.CANCEL, color = VvMuted) }
        },
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MyVehiclesScreenPreview() {
    VinculoVialTheme {
        MyVehiclesScreen(
            state = VehiclesUiState(count = VehicleCount(count = 1, max = 3), loading = false),
            onBack = {},
            onLink = {},
            onUnlink = {},
            onUnlinkAll = {},
            onRetry = {},
            onPlateChange = {},
            onPlateContinue = {},
            onPlateBack = {},
            onPlateConfirm = {},
            onUnlinkAllConfirm = {},
            onDialogDismiss = {},
        )
    }
}
