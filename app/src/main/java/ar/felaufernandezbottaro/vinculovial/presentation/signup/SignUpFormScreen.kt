package ar.felaufernandezbottaro.vinculovial.presentation.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialField
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialProblem
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialRules
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvTextField
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/** Pantalla "Crear una cuenta" del wireframe. */
@Composable
fun SignUpFormScreen(
    state: SignUpUiState,
    onDniChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmationChange: (String) -> Unit,
    onValidateIdentity: () -> Unit,
    onGoToLogin: () -> Unit,
) {
    var termsAccepted by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Crear una cuenta",
                style = MaterialTheme.typography.displaySmall,
                color = VvNavy,
                textAlign = TextAlign.Center,
            )

            LoginLink(onGoToLogin = onGoToLogin)

            VvTextField(
                label = "DNI",
                value = state.form.dni,
                onValueChange = onDniChange,
                placeholder = "12345678",
                errorText = state.fieldErrors[CredentialField.DNI]?.message(),
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
            )

            VvTextField(
                label = "EMAIL",
                value = state.form.email,
                onValueChange = onEmailChange,
                placeholder = "juanperez@gmail.com",
                errorText = state.fieldErrors[CredentialField.EMAIL]?.message(),
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )

            VvTextField(
                label = "CONTRASEÑA",
                value = state.form.password,
                onValueChange = onPasswordChange,
                placeholder = "••••••••",
                errorText = passwordErrorText(state.fieldErrors[CredentialField.PASSWORD], state.form.password),
                enabled = !state.isSubmitting,
                isPassword = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
            )

            VvTextField(
                label = "VALIDAR CONTRASEÑA",
                value = state.form.passwordConfirmation,
                onValueChange = onPasswordConfirmationChange,
                placeholder = "••••••••",
                errorText = state.fieldErrors[CredentialField.PASSWORD_CONFIRMATION]?.message(),
                enabled = !state.isSubmitting,
                isPassword = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )

            TermsAndConditionsCheckbox(
                checked = termsAccepted,
                onCheckedChange = { termsAccepted = it },
                onReadTerms = { showTermsDialog = true },
                enabled = !state.isSubmitting,
            )

            Spacer(Modifier.height(8.dp))

            VvPrimaryButton(
                text = "Validar identidad",
                onClick = onValidateIdentity,
                enabled = !state.isSubmitting && termsAccepted,
            )
        }
    }

    if (showTermsDialog) {
        TermsAndConditionsDialog(onDismiss = { showTermsDialog = false })
    }
}

/**
 * Casilla obligatoria: sin aceptarla, "Validar identidad" queda deshabilitado. El texto es
 * parcialmente clicable para poder leer las condiciones antes de aceptarlas, no sólo tildar
 * a ciegas.
 */
@Composable
private fun TermsAndConditionsCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onReadTerms: () -> Unit,
    enabled: Boolean,
) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = VvMuted)) { append("Acepto los ") }
        withStyle(SpanStyle(color = VvNavy, textDecoration = TextDecoration.Underline)) {
            append("Términos y Condiciones")
        }
        withStyle(SpanStyle(color = VvMuted)) { append(" de la plataforma") }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        ClickableText(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            onClick = { onReadTerms() },
        )
    }
}

@Composable
private fun TermsAndConditionsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Términos y Condiciones") },
        text = {
            Text(
                "Al registrarte, tu DNI y tu patente se cifran de punto a punto: cada dato " +
                    "viaja cifrado para su destinatario final y nadie más puede leerlo en " +
                    "tránsito. Las imágenes que tomás para validar tu identidad (documento y " +
                    "selfie) se procesan en tu propio dispositivo y se eliminan de forma " +
                    "permanente apenas termina la validación, sin copias en disco ni en " +
                    "nuestros servidores. Quien reporta un incidente permanece siempre " +
                    "anónimo: ninguna pantalla de la aplicación muestra, sugiere ni insinúa " +
                    "quién lo hizo.",
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendido", color = VvNavy) }
        },
    )
}

internal fun passwordErrorText(problem: CredentialProblem?, password: String): String? = when (problem) {
    null -> null
    CredentialProblem.REQUIRED -> problem.message()
    else -> CredentialRules.unmetPasswordRequirements(password).message()
}

@Composable
private fun LoginLink(onGoToLogin: () -> Unit) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = VvMuted)) { append("¿Ya tenés una cuenta? ") }
        withStyle(SpanStyle(color = VvNavy, textDecoration = TextDecoration.Underline)) {
            append("Iniciá sesión aquí")
        }
    }
    ClickableText(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        onClick = { onGoToLogin() },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SignUpFormPreview() {
    VinculoVialTheme {
        SignUpFormScreen(
            state = SignUpUiState(),
            onDniChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onPasswordConfirmationChange = {},
            onValidateIdentity = {},
            onGoToLogin = {},
        )
    }
}
