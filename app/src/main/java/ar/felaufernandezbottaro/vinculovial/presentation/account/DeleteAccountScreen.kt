package ar.felaufernandezbottaro.vinculovial.presentation.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountError
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvCircleIconButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvTextField
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange

/**
 * "Eliminar cuenta" (US-11): el aviso de qué se borra y qué se conserva, y debajo la contraseña
 * que confirma. El aviso queda a la vista mientras se tipea: quien confirma sabe a qué.
 *
 * Mientras la baja está en curso no se puede salir ni volver a tocar nada: una request a medias
 * cancelada por la salida dejaría al usuario sin saber si la cuenta se borró.
 */
@Composable
fun DeleteAccountScreen(
    state: DeleteAccountUiState,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(enabled = state.isSubmitting) {}

    val error = state.error
    val passwordError = when {
        error == DeleteAccountError.InvalidPassword -> error?.message()
        state.passwordMissing -> DeleteAccountTexts.PASSWORD_REQUIRED
        else -> null
    }
    // Todo error salvo la contraseña (que se muestra en el campo) va aparte, sobre el botón.
    val generalError = error?.takeIf { it != DeleteAccountError.InvalidPassword }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { VvBottomBar() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VvCircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver a la configuración",
                onClick = onBack,
                enabled = !state.isSubmitting,
            )

            Text(
                text = DeleteAccountTexts.TITLE,
                style = MaterialTheme.typography.displaySmall,
                color = VvNavy,
            )

            DeleteAccountTexts.infoParagraphs.forEach { paragraph ->
                Text(
                    text = paragraph,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = if (paragraph == DeleteAccountTexts.IRREVERSIBLE) FontWeight.Bold else null,
                )
            }

            Spacer(Modifier.height(4.dp))

            VvTextField(
                label = DeleteAccountTexts.PASSWORD_LABEL,
                value = state.password,
                onValueChange = onPasswordChange,
                placeholder = "••••••••",
                errorText = passwordError,
                enabled = !state.isSubmitting,
                isPassword = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
            )

            generalError?.let {
                Text(
                    text = it.message(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            VvPrimaryButton(
                text = if (generalError?.isRetryable() == true) DeleteAccountTexts.RETRY else DeleteAccountTexts.CONFIRM,
                onClick = onConfirm,
                enabled = !state.isSubmitting,
                loading = state.isSubmitting,
                containerColor = VvOrange,
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DeleteAccountScreenPreview() {
    VinculoVialTheme {
        DeleteAccountScreen(
            state = DeleteAccountUiState(),
            onPasswordChange = {},
            onConfirm = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DeleteAccountScreenWrongPasswordPreview() {
    VinculoVialTheme {
        DeleteAccountScreen(
            state = DeleteAccountUiState(
                password = "equivocada",
                status = DeleteAccountStatus.Failed(DeleteAccountError.InvalidPassword),
            ),
            onPasswordChange = {},
            onConfirm = {},
            onBack = {},
        )
    }
}
