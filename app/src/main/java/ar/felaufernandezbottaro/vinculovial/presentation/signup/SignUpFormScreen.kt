package ar.felaufernandezbottaro.vinculovial.presentation.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                errorText = state.fieldErrors[CredentialField.PASSWORD]?.message(),
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

            Spacer(Modifier.height(8.dp))

            VvPrimaryButton(
                text = "Validar identidad",
                onClick = onValidateIdentity,
                enabled = !state.isSubmitting,
            )
        }
    }
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
