package com.vinculovial.app.presentation.signup

import com.vinculovial.app.domain.signup.SignUpError
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
    val capturing: CaptureTarget? = null,
    val status: SignUpStatus = SignUpStatus.Idle,
) {
    val isSubmitting: Boolean get() = status is SignUpStatus.Submitting

    val isBusy: Boolean get() = isSubmitting || capturing != null

    val isFormComplete: Boolean
        get() = form.dni.isNotBlank() &&
            form.email.isNotBlank() &&
            form.password.isNotBlank() &&
            form.passwordConfirmation.isNotBlank()

    val canSubmitCapture: Boolean get() = dniImageCaptured && selfieCaptured && !isBusy

    val error: SignUpError? get() = (status as? SignUpStatus.Failed)?.error
}

enum class CaptureTarget { DNI, SELFIE }

sealed interface SignUpStatus {
    /** Editando el formulario o esperando la captura. */
    data object Idle : SignUpStatus

    /** Pipeline en curso: Edge -> HPKE -> attestation -> POST. */
    data object Submitting : SignUpStatus

    /** 201: la cuenta quedó creada. US-01 no devuelve sesión (eso es US-02). */
    data object Registered : SignUpStatus

    data class Failed(val error: SignUpError) : SignUpStatus
}
