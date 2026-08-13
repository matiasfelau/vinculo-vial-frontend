package com.vinculovial.app.presentation.signup

import com.vinculovial.app.domain.signup.model.CredentialField
import com.vinculovial.app.domain.signup.model.CredentialProblem
import com.vinculovial.app.domain.signup.model.SignUpForm

/**
 * Estado único de la pantalla de registro (US-01).
 *
 * Nunca contiene bytes de imagen: la captura vive en `data/edge` y se descarta
 * apenas se extraen los datos (Zero Retention).
 */
data class SignUpUiState(
    val form: SignUpForm = SignUpForm(),
    val fieldErrors: Map<CredentialField, CredentialProblem> = emptyMap(),
    val dniImageCaptured: Boolean = false,
    val selfieCaptured: Boolean = false,
    val status: SignUpStatus = SignUpStatus.Idle,
) {
    val isSubmitting: Boolean get() = status is SignUpStatus.Submitting

    val isFormComplete: Boolean
        get() = form.dni.isNotBlank() &&
            form.email.isNotBlank() &&
            form.password.isNotBlank() &&
            form.passwordConfirmation.isNotBlank()

    val canSubmitCapture: Boolean get() = dniImageCaptured && selfieCaptured && !isSubmitting
}

sealed interface SignUpStatus {
    /** Editando el formulario o esperando la captura. */
    data object Idle : SignUpStatus

    /** Pipeline en curso: Edge -> HPKE -> attestation -> POST. */
    data object Submitting : SignUpStatus
}
