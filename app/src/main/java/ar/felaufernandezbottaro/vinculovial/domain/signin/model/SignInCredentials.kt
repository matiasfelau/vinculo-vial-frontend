package ar.felaufernandezbottaro.vinculovial.domain.signin.model

/**
 * Payload interno del contrato US-02: lo único que se cifra y viaja a Identity.
 */
data class SignInCredentials(
    val dni: String,
    val password: String,
) {
    override fun toString(): String = "SignInCredentials(***)"
}
