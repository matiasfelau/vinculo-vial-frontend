package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.Credentials
import com.vinculovial.app.domain.signup.model.EdgeResult

/** Edge falso: devuelve el resultado programado y registra qué se le pidió. */
class FakeIdentityVerificationService(
    var result: EdgeResult = EdgeResult(dni = "12345678", isLive = true),
) : IdentityVerificationService {

    var capturedDniClaims = mutableListOf<String>()
    var selfieCaptures = 0
    var verifications = 0
    var discards = 0

    /** Si no es `null`, aceptar una captura falla con esta excepción. */
    var captureFailure: Exception? = null

    override suspend fun acceptDniFront(image: ByteArray, declaredDni: String) {
        captureFailure?.let { throw it }
        capturedDniClaims += declaredDni
    }

    override suspend fun acceptSelfie(image: ByteArray) {
        captureFailure?.let { throw it }
        selfieCaptures++
    }

    override suspend fun verifyAndDiscard(): EdgeResult {
        verifications++
        return result
    }

    override fun discardCaptures() {
        discards++
    }
}

/** Repositorio falso: registra las credenciales recibidas y devuelve lo programado. */
class FakeSignUpRepository(
    var result: SignUpResult = SignUpResult.Registered,
) : SignUpRepository {

    val received = mutableListOf<Credentials>()

    override suspend fun signUp(credentials: Credentials): SignUpResult {
        received += credentials
        return result
    }
}
