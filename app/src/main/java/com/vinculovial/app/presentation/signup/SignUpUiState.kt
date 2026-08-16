package com.vinculovial.app.presentation.signup

import com.vinculovial.app.domain.signup.SignUpError
import com.vinculovial.app.domain.signup.model.CredentialField
import com.vinculovial.app.domain.signup.model.CredentialProblem
import com.vinculovial.app.domain.signup.model.SignUpForm

/**
 * Estado único de la pantalla de registro (US-01).
 *
 * Nunca contiene bytes de imagen ni embeddings: todo eso vive en `data/edge` y se
 * descarta apenas termina la verificación (Zero Retention).
 */
data class SignUpUiState(
    val form: SignUpForm = SignUpForm(),
    val fieldErrors: Map<CredentialField, CredentialProblem> = emptyMap(),
    val completedCaptures: Set<CaptureTarget> = emptySet(),
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

    fun isCaptured(target: CaptureTarget): Boolean = target in completedCaptures

    val canSubmitCapture: Boolean
        get() = completedCaptures.containsAll(CaptureTarget.entries) && !isBusy

    val error: SignUpError? get() = (status as? SignUpStatus.Failed)?.error
}

/**
 * Los dos pasos del onboarding, tal como los ve el usuario.
 *
 * El escaneo del documento detecta PDF417 (DNI tarjeta) y QR (DNI con chip) al mismo
 * tiempo, sin que el usuario elija formato — ver `MlKitDocumentPresenceSession`. La
 * *detección* cubre los dos; la *lectura* del contenido del QR todavía no, porque RENAPER
 * no publicó ese formato — ver `ElectronicDniQrParser`.
 */
enum class CaptureTarget {
    /** Documento (DNI): frente si es tarjeta, reverso si es el nuevo con chip. */
    DOCUMENT,

    /** Selfie, tomada sólo después de superar la prueba de vida. */
    SELFIE,
}

sealed interface SignUpStatus {
    /** Editando el formulario o esperando capturas. */
    data object Idle : SignUpStatus

    /** Pipeline en curso: Edge -> HPKE -> attestation -> POST. */
    data object Submitting : SignUpStatus

    /** 201: la cuenta quedó creada. US-01 no devuelve sesión (eso es US-02). */
    data object Registered : SignUpStatus

    data class Failed(val error: SignUpError) : SignUpStatus
}
