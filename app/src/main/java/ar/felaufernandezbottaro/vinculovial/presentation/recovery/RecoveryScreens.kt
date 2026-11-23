package ar.felaufernandezbottaro.vinculovial.presentation.recovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialField
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeEntryScreen
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeFlowTexts
import ar.felaufernandezbottaro.vinculovial.presentation.common.message
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvCircleIconButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvTextField
import ar.felaufernandezbottaro.vinculovial.presentation.signup.message
import ar.felaufernandezbottaro.vinculovial.presentation.signup.passwordErrorText
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvCream
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange

/** Paso 1 de la recuperación: el DNI de la cuenta. Sólo muestra estado y emite eventos. */
@Composable
fun RecoveryDniScreen(
    state: RecoveryUiState,
    onDniChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
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
                    contentDescription = "Volver al inicio de sesión",
                    onClick = onBack,
                    enabled = !state.requesting,
                )
            }

            Text(
                text = "Recuperá tu contraseña",
                style = MaterialTheme.typography.displaySmall,
                color = VvNavy,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "Ingresá el DNI de tu cuenta. Te vamos a mandar un código al email que registraste " +
                    "y, para cuidar tu cuenta, después vamos a validar tu identidad con tu DNI y una selfie.",
                style = MaterialTheme.typography.bodyMedium,
                color = VvMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            VvTextField(
                label = "DNI",
                value = state.dni,
                onValueChange = onDniChange,
                placeholder = "12345678",
                errorText = state.dniProblem?.message() ?: state.requestError?.message(),
                enabled = !state.requesting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )

            Spacer(Modifier.height(4.dp))

            VvPrimaryButton(
                text = "Enviar código",
                onClick = onSubmit,
                enabled = !state.requesting && state.dni.isNotBlank(),
                loading = state.requesting,
            )
        }
    }
}

/** Paso 2: el código de 6 dígitos. El aviso es el mismo exista o no la cuenta (anti-enumeración). */
@Composable
fun RecoveryCodeScreen(
    state: RecoveryUiState,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    CodeEntryScreen(
        title = "Ingresá el código",
        description = CodeFlowTexts.RECOVERY_REQUESTED,
        code = state.code,
        onCodeChange = onCodeChange,
        errorText = state.codeError?.message(),
        confirmEnabled = state.canConfirmCode,
        busy = state.isCodeBusy,
        resendEnabled = state.canResend,
        resendLabel = if (state.resendSeconds > 0) {
            CodeFlowTexts.resendIn(state.resendSeconds)
        } else {
            CodeFlowTexts.RESEND_AVAILABLE
        },
        onConfirm = onConfirm,
        onResend = onResend,
        onBack = onBack,
        backDescription = "Volver para corregir el DNI",
    )
}

/**
 * Paso 3, y punto de partida de la captura: elige la contraseña nueva y de acá se sale a escanear
 * el DNI y a la prueba de vida. Al volver de la selfie el envío arranca solo y esta pantalla muestra
 * el progreso o el error.
 */
@Composable
fun RecoveryPasswordScreen(
    state: RecoveryUiState,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmationChange: (String) -> Unit,
    onStartCapture: () -> Unit,
    onErrorDismissed: () -> Unit,
    onEditPassword: () -> Unit,
    onRequestNewCode: () -> Unit,
    onBack: () -> Unit,
) {
    // Mientras se envía, el back se ignora: salir a mitad del envío abandonaría una request en vuelo.
    BackHandler {
        if (!state.isSubmitting) onBack()
    }

    state.error?.let { error ->
        RecoveryErrorDialog(
            error = error,
            onDismiss = onErrorDismissed,
            onEditPassword = onEditPassword,
            onRequestNewCode = onRequestNewCode,
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                VvCircleIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    onClick = onBack,
                    enabled = !state.isSubmitting,
                )
            }

            Text(
                text = "Elegí tu contraseña nueva",
                style = MaterialTheme.typography.displaySmall,
                color = VvNavy,
                textAlign = TextAlign.Center,
            )

            VvTextField(
                label = "CONTRASEÑA NUEVA",
                value = state.password,
                onValueChange = onPasswordChange,
                placeholder = "••••••••",
                errorText = passwordErrorText(state.passwordProblems[CredentialField.PASSWORD], state.password),
                enabled = !state.isSubmitting,
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            )

            VvTextField(
                label = "VALIDAR CONTRASEÑA",
                value = state.passwordConfirmation,
                onValueChange = onPasswordConfirmationChange,
                placeholder = "••••••••",
                errorText = state.passwordProblems[CredentialField.PASSWORD_CONFIRMATION]?.message(),
                enabled = !state.isSubmitting,
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )

            Text(
                text = if (state.isSubmitting) {
                    "Estamos validando tu identidad. Puede tardar unos segundos."
                } else {
                    "Para confirmar que sos vos, ahora vamos a escanear tu DNI y a pedirte una selfie. " +
                        "Las imágenes se procesan en tu dispositivo y no se guardan."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = VvMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            VvPrimaryButton(
                text = "Escanear mi DNI",
                onClick = onStartCapture,
                enabled = !state.isSubmitting && state.password.isNotBlank() && state.passwordConfirmation.isNotBlank(),
                loading = state.isSubmitting,
            )
        }
    }
}

/** Paso final: la contraseña cambió y todas las sesiones se cerraron. */
@Composable
fun RecoveryDoneScreen(onGoToLogin: () -> Unit) {
    // Atrás no vuelve a la recuperación (ya terminó): lleva al mismo lugar que el botón.
    BackHandler(onBack = onGoToLogin)

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
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Cambiaste tu contraseña",
                style = MaterialTheme.typography.displaySmall,
                color = VvNavy,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Por seguridad cerramos todas tus sesiones. Ingresá con tu nueva contraseña.",
                style = MaterialTheme.typography.bodyLarge,
                color = VvMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            VvPrimaryButton(text = "Iniciar sesión", onClick = onGoToLogin)
        }
    }
}

/** Diálogo único para los errores del cierre de la recuperación: mensaje genérico y una acción concreta. */
@Composable
fun RecoveryErrorDialog(
    error: RecoveryError,
    onDismiss: () -> Unit,
    onEditPassword: () -> Unit,
    onRequestNewCode: () -> Unit,
) {
    val presentation = error.presentation()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VvCream,
        icon = {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = VvOrange,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(text = presentation.title, style = MaterialTheme.typography.headlineMedium, color = VvNavy)
        },
        text = { Text(text = presentation.message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            when (presentation.action) {
                RecoveryErrorAction.RETRY_CAPTURE -> TextButton(onClick = onDismiss) {
                    Text("Reintentar", color = VvNavy)
                }

                RecoveryErrorAction.EDIT_PASSWORD -> TextButton(onClick = onEditPassword) {
                    Text("Cambiar la contraseña", color = VvNavy)
                }

                RecoveryErrorAction.REQUEST_NEW_CODE -> TextButton(onClick = onRequestNewCode) {
                    Text("Pedir otro código", color = VvNavy)
                }

                RecoveryErrorAction.DISMISS -> TextButton(onClick = onDismiss) {
                    Text("Entendido", color = VvNavy)
                }
            }
        },
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun RecoveryDniScreenPreview() {
    VinculoVialTheme {
        RecoveryDniScreen(state = RecoveryUiState(), onDniChange = {}, onSubmit = {}, onBack = {})
    }
}
