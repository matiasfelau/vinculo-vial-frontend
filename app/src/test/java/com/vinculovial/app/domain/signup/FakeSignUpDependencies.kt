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
    var failOnCapture = false

    override suspend fun captureDniFront(claimedDni: String) {
        if (failOnCapture) error("cámara no disponible")
        capturedDniClaims += claimedDni
    }

    override suspend fun captureSelfie() {
        if (failOnCapture) error("cámara no disponible")
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
