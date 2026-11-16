package ar.felaufernandezbottaro.vinculovial.presentation.signup

import androidx.compose.runtime.Composable
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeEntryScreen
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeFlowTexts
import ar.felaufernandezbottaro.vinculovial.presentation.common.message

/**
 * Pantalla "Verificá tu email" entre el formulario y el consentimiento (US-13). Pide el código de
 * 6 dígitos que llegó por email; "Reenviar" tiene su cuenta regresiva y volver permite corregir el
 * email.
 *
 * El texto es el mismo exista o no una cuenta con ese email: el servidor responde igual y la app
 * no puede ni debe distinguirlo.
 */
@Composable
fun VerifyEmailScreen(
    state: VerifyEmailUiState,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    CodeEntryScreen(
        title = "Verificá tu email",
        description = "Te enviamos un código de 6 dígitos a ${state.email}. Ingresalo para seguir con " +
            "tu registro. Si te equivocaste de email, volvé y corregilo.",
        code = state.code,
        onCodeChange = onCodeChange,
        errorText = state.error?.message(),
        confirmEnabled = state.canConfirm,
        busy = state.isBusy,
        resendEnabled = state.canResend,
        resendLabel = if (state.resendSeconds > 0) {
            CodeFlowTexts.resendIn(state.resendSeconds)
        } else {
            CodeFlowTexts.RESEND_AVAILABLE
        },
        onConfirm = onConfirm,
        onResend = onResend,
        onBack = onBack,
        backDescription = "Volver al formulario para corregir el email",
    )
}
