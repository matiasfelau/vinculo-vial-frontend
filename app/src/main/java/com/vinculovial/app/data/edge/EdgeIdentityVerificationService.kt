package com.vinculovial.app.data.edge

import com.vinculovial.app.di.DefaultDispatcher
import com.vinculovial.app.domain.signup.IdentityVerificationService
import com.vinculovial.app.domain.signup.model.EdgeResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pipeline Edge de US-01: recibe las fotos que tomó la cámara, las retiene sólo el
 * tiempo necesario para correr OCR + Liveness y las libera inmediatamente después.
 *
 * Es `@Singleton` porque la sesión de captura atraviesa varias pantallas, y las imágenes
 * nunca salen de esta clase: hacia afuera sólo viaja [EdgeResult].
 */
@Singleton
class EdgeIdentityVerificationService @Inject constructor(
    private val ocrEngine: OcrEngine,
    private val livenessDetector: LivenessDetector,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : IdentityVerificationService {

    /** Serializa capturas y verificación: nunca dos accesos concurrentes a las imágenes. */
    private val mutex = Mutex()

    private var dniFront: SecureImageBuffer? = null
    private var selfie: SecureImageBuffer? = null
    private var declaredDni: String = ""

    override suspend fun acceptDniFront(image: ByteArray, declaredDni: String) {
        // wrap() toma posesión de los bytes: a partir de acá la única referencia viva
        // está dentro del buffer, que se sabe borrar.
        val captured = SecureImageBuffer.wrap(image)
        withContext(dispatcher) {
            mutex.withLock {
                dniFront?.clear()
                dniFront = captured
                this@EdgeIdentityVerificationService.declaredDni = declaredDni
            }
        }
    }

    override suspend fun acceptSelfie(image: ByteArray) {
        val captured = SecureImageBuffer.wrap(image)
        withContext(dispatcher) {
            mutex.withLock {
                selfie?.clear()
                selfie = captured
            }
        }
    }

    override suspend fun verifyAndDiscard(): EdgeResult = withContext(dispatcher) {
        mutex.withLock {
            val dniBuffer = dniFront
            val selfieBuffer = selfie
            try {
                if (dniBuffer == null || selfieBuffer == null) {
                    return@withLock EdgeResult(dni = null, isLive = false)
                }
                // Los bytes se prestan sólo dentro de estos bloques; ni el OCR ni el
                // Liveness reciben nada que puedan conservar.
                dniBuffer.read { dniBytes ->
                    selfieBuffer.read { selfieBytes ->
                        EdgeResult(
                            dni = ocrEngine.extractDni(dniBytes, declaredDni),
                            isLive = livenessDetector.isLive(dniBytes, selfieBytes),
                        )
                    }
                }
            } finally {
                // Zero Retention: pase lo que pase (incluida una excepción de un motor),
                // las imágenes no sobreviven a esta llamada.
                releaseImages()
            }
        }
    }

    override fun discardCaptures() {
        releaseImages()
    }

    private fun releaseImages() {
        dniFront?.clear()
        selfie?.clear()
        dniFront = null
        selfie = null
        declaredDni = ""
    }
}
