package ar.felaufernandezbottaro.vinculovial.data.edge

import ar.felaufernandezbottaro.vinculovial.di.DefaultDispatcher
import ar.felaufernandezbottaro.vinculovial.domain.signup.IdentityVerificationService
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentIdentity
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.EdgeResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Onboarding local: lo único que corre en el dispositivo.
 *
 * **Pivote RENAPER-Ready**: la comparación de caras la hace el backend contra el padrón del
 * Estado. **Escaneo en vivo**: el documento ya no se fotografía — `DocumentScanSession`
 * decodifica su código del stream de la cámara y entrega los campos ya parseados, así que
 * acá no hay ninguna imagen de documento que sostener ni que borrar.
 *
 * Lo que queda del lado del Edge:
 * 1. sostener los datos del documento hasta que esté también la selfie;
 * 2. la prueba de vida, que sigue siendo 100% local — es la única defensa en el dispositivo
 *    contra una foto-de-una-foto antes de gastar una llamada de red;
 * 3. comprimir la selfie para que entre en el payload.
 *
 * Zero Retention: la única imagen que existe acá es la selfie, y sólo hasta que
 * [SelfieCompressor] produce la versión que efectivamente viaja. La del documento no existe
 * en absoluto — no es que se borre bien, es que nunca se materializa.
 */
@Singleton
class EdgeIdentityVerificationService @Inject constructor(
    private val selfieCompressor: SelfieCompressor,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : IdentityVerificationService {

    /** Serializa capturas y verificación: nunca dos accesos concurrentes a la selfie. */
    private val mutex = Mutex()

    private var document: DocumentIdentity? = null
    private var selfie: SecureImageBuffer? = null
    private var livenessPassed = false

    override suspend fun acceptDocument(document: DocumentIdentity) {
        withContext(dispatcher) {
            mutex.withLock { this@EdgeIdentityVerificationService.document = document }
        }
    }

    override suspend fun acceptSelfie(image: ByteArray, livenessPassed: Boolean) {
        // wrap() toma posesión de los bytes: la única referencia viva queda dentro del
        // buffer, que se sabe borrar.
        val captured = SecureImageBuffer.wrap(image)
        withContext(dispatcher) {
            mutex.withLock {
                selfie?.clear()
                selfie = captured
                this@EdgeIdentityVerificationService.livenessPassed = livenessPassed
            }
        }
    }

    override suspend fun verifyAndDiscard(): EdgeResult = withContext(dispatcher) {
        mutex.withLock {
            try {
                verify()
            } finally {
                // Zero Retention: pase lo que pase, la selfie original no sobrevive a esta
                // llamada. Lo único que sale con vida es el string ya comprimido que
                // devuelve verify(), que es exactamente el que está diseñado para viajar.
                releaseCaptures()
            }
        }
    }

    override fun discardCaptures() = releaseCaptures()

    private suspend fun verify(): EdgeResult {
        val document = document
        val selfieBuffer = selfie

        if (document == null || selfieBuffer == null) {
            return EdgeResult.Rejected(EdgeResult.Reason.INCOMPLETE_CAPTURE)
        }

        // La prueba de vida se corrió sobre el preview; si no se superó, no hay nada que
        // mandar: una foto de una foto no debe llegar ni siquiera al backend.
        if (!livenessPassed) {
            return EdgeResult.Rejected(EdgeResult.Reason.LIVENESS_FAILED)
        }

        val selfieBase64 = selfieBuffer.readSuspending { jpeg -> selfieCompressor.compress(jpeg) }
            ?: return EdgeResult.Rejected(EdgeResult.Reason.SELFIE_UNUSABLE)

        return EdgeResult.Verified(document = document, selfieBase64 = selfieBase64)
    }

    private fun releaseCaptures() {
        selfie?.clear()
        selfie = null
        document = null
        livenessPassed = false
    }
}
