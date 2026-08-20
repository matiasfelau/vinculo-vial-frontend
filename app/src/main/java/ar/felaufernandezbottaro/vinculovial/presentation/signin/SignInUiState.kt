package ar.felaufernandezbottaro.vinculovial.presentation.signin

import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInError
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInField
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInFieldProblem
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInForm

/** Estado único de la pantalla de login (US-02). */
data class SignInUiState(
    val form: SignInForm = SignInForm(),
    val fieldErrors: Map<SignInField, SignInFieldProblem> = emptyMap(),
    val status: SignInStatus = SignInStatus.Idle,
) {
    val isSubmitting: Boolean get() = status is SignInStatus.Submitting

    val error: SignInError? get() = (status as? SignInStatus.Failed)?.error
}

sealed interface SignInStatus {
    /** Editando el formulario. */
    data object Idle : SignInStatus

    /** Pipeline en curso: HPKE -> attestation -> POST -> persistencia de tokens. */
    data object Submitting : SignInStatus

    /** 200: el par de tokens ya quedó guardado en SecureTokenStore. */
    data object SignedIn : SignInStatus

    data class Failed(val error: SignInError) : SignInStatus
}
