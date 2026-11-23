package ar.felaufernandezbottaro.vinculovial.presentation.signin

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.domain.session.LoginNotice
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInField
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvTextField
import ar.felaufernandezbottaro.vinculovial.presentation.session.message
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/** Pantalla "Iniciar sesión": DNI + contraseña, la puerta de entrada para quien ya tiene cuenta. */
@Composable
fun SignInScreen(
    state: SignInUiState,
    notice: LoginNotice?,
    onDniChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoToSignUp: () -> Unit,
    onForgotPassword: () -> Unit = {},
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
                text = "Iniciar sesión",
                style = MaterialTheme.typography.displaySmall,
                color = VvNavy,
                textAlign = TextAlign.Center,
            )

            // Por qué se llegó acá sin pedirlo: sesión vencida o huellas cambiadas (US-12).
            notice?.let {
                Text(
                    text = it.message(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VvNavy,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SignUpLink(onGoToSignUp = onGoToSignUp)

            VvTextField(
                label = "DNI",
                value = state.form.dni,
                onValueChange = onDniChange,
                placeholder = "12345678",
                errorText = state.fieldErrors[SignInField.DNI]?.message(),
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
            )

            VvTextField(
                label = "CONTRASEÑA",
                value = state.form.password,
                onValueChange = onPasswordChange,
                placeholder = "••••••••",
                errorText = state.fieldErrors[SignInField.PASSWORD]?.message(),
                enabled = !state.isSubmitting,
                isPassword = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )

            // Genérico y visible apenas llega, sin distinguir cuál de los dos campos falló
            // — ver SignInError.InvalidCredentials.
            state.error?.let { error ->
                Text(
                    text = error.message(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))

            VvPrimaryButton(
                text = "Iniciar sesión",
                onClick = onSubmit,
                enabled = !state.isSubmitting,
                loading = state.isSubmitting,
            )

            TextButton(onClick = onForgotPassword, enabled = !state.isSubmitting) {
                Text(text = "¿Olvidaste tu contraseña?", color = VvNavy)
            }
        }
    }
}

@Composable
private fun SignUpLink(onGoToSignUp: () -> Unit) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = VvMuted)) { append("¿Todavía no tenés una cuenta? ") }
        withStyle(SpanStyle(color = VvNavy, textDecoration = TextDecoration.Underline)) {
            append("Registrate aquí")
        }
    }
    ClickableText(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        onClick = { onGoToSignUp() },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SignInScreenPreview() {
    VinculoVialTheme {
        SignInScreen(
            state = SignInUiState(),
            notice = null,
            onDniChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onGoToSignUp = {},
        )
    }
}
