package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.CameraFrame
import com.vinculovial.app.domain.signup.model.Credentials
import com.vinculovial.app.domain.signup.model.DniFrontQuality
import com.vinculovial.app.domain.signup.model.DocumentPresence
import com.vinculovial.app.domain.signup.model.DocumentPresenceStage
import com.vinculovial.app.domain.signup.model.EdgeResult
import com.vinculovial.app.domain.signup.model.LivenessChallenge
import com.vinculovial.app.domain.signup.model.LivenessProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Edge falso: devuelve el resultado programado y registra qué se le entregó. */
class FakeIdentityVerificationService(
    var result: EdgeResult = EdgeResult.Verified(dni = "12345678", faceSimilarity = 0.9f),
) : IdentityVerificationService {

    var dniFrontCaptures = 0
    var selfieCaptures = 0
    var lastLivenessPassed: Boolean? = null
    var verifications = 0
    var discards = 0

    /** Si no es `null`, aceptar una captura falla con esta excepción. */
    var captureFailure: Exception? = null

    override suspend fun acceptDniFront(image: ByteArray) {
        captureFailure?.let { throw it }
        dniFrontCaptures++
    }

    var canReadDniFrontResult = DniFrontQuality.READABLE
    var canReadDniFrontCalls = 0

    override suspend fun canReadDniFront(image: ByteArray): DniFrontQuality {
        canReadDniFrontCalls++
        return canReadDniFrontResult
    }

    override suspend fun acceptSelfie(image: ByteArray, livenessPassed: Boolean) {
        captureFailure?.let { throw it }
        selfieCaptures++
        lastLivenessPassed = livenessPassed
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

/** Sesión de presencia inerte: el ViewModel sólo la expone hacia la pantalla de documento. */
class FakeDocumentPresenceSession : DocumentPresenceSession {

    private val _presence = MutableStateFlow(DocumentPresence())
    override val presence: StateFlow<DocumentPresence> = _presence

    var starts = 0
    var pauses = 0
    var closes = 0

    /** Simula que la cámara ya ve el documento quieto y encuadrado. */
    fun emitSteady() {
        _presence.value = DocumentPresence(
            stage = DocumentPresenceStage.STEADY,
            hasBarcode = true,
            hasFace = true,
        )
    }

    override fun start() {
        starts++
        _presence.value = DocumentPresence()
    }

    override suspend fun submitFrame(frame: CameraFrame) = Unit

    override fun pause() {
        pauses++
    }

    override fun close() {
        closes++
    }
}

/** Sesión de liveness inerte: el ViewModel sólo la expone hacia la pantalla de selfie. */
class FakeLivenessSession : LivenessSession {

    private val _progress = MutableStateFlow(LivenessProgress(challenge = LivenessChallenge.SMILE))
    override val progress: StateFlow<LivenessProgress> = _progress

    var starts = 0
    var closes = 0

    override fun start() {
        starts++
    }

    override suspend fun submitFrame(frame: CameraFrame) = Unit

    override fun close() {
        closes++
    }
}
