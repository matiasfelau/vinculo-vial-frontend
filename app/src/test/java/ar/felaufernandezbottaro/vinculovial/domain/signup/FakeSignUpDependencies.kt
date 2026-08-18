package ar.felaufernandezbottaro.vinculovial.domain.signup

import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CameraFrame
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.Credentials
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentIdentity
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentScanStage
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentScanState
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.EdgeResult
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.LivenessChallenge
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.LivenessProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Edge falso: devuelve el resultado programado y registra qué se le entregó. */
class FakeIdentityVerificationService(
    var result: EdgeResult = EdgeResult.Verified(
        document = DocumentIdentity(number = "12345678", sex = "M", procedureNumber = "00307513148"),
        selfieBase64 = "c2VsZmll",
    ),
) : IdentityVerificationService {

    var documentCaptures = 0
    var lastDocument: DocumentIdentity? = null
    var selfieCaptures = 0
    var lastLivenessPassed: Boolean? = null
    var verifications = 0
    var discards = 0

    /** Si no es `null`, aceptar una captura falla con esta excepción. */
    var captureFailure: Exception? = null

    override suspend fun acceptDocument(document: DocumentIdentity) {
        captureFailure?.let { throw it }
        documentCaptures++
        lastDocument = document
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

/** Sesión de escaneo inerte: el ViewModel sólo la expone hacia la pantalla de documento. */
class FakeDocumentScanSession : DocumentScanSession {

    private val _state = MutableStateFlow(DocumentScanState())
    override val state: StateFlow<DocumentScanState> = _state

    var starts = 0
    var closes = 0
    var attachedZoomControllers = 0

    /** Simula que el escáner ya leyó el código. */
    fun emitDecoded(document: DocumentIdentity) {
        _state.value = DocumentScanState(stage = DocumentScanStage.DECODED, document = document)
    }

    override fun attachZoomController(controller: ZoomController) {
        attachedZoomControllers++
    }

    override fun start() {
        starts++
        _state.value = DocumentScanState()
    }

    override suspend fun submitFrame(frame: CameraFrame) = Unit

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
