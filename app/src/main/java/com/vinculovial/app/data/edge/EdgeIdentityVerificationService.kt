package com.vinculovial.app.data.edge

import com.vinculovial.app.di.DefaultDispatcher
import com.vinculovial.app.domain.signup.IdentityVerificationService
import com.vinculovial.app.domain.signup.model.EdgeResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pipeline Edge de US-01: mantiene las dos imágenes capturadas sólo el tiempo necesario
 * para correr OCR + Liveness y las descarta inmediatamente después.
 *
 * Es `@Singleton` porque la sesión de captura atraviesa varias pantallas, y las imágenes
 * nunca salen de esta clase: hacia afuera sólo viaja [EdgeResult].
 */
@Singleton
class EdgeIdentityVerificationService @Inject constructor(
    private val camera: MockCamera,
    private val ocrEngine: OcrEngine,
    private val livenessDetector: LivenessDetector,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : IdentityVerificationService {

    /** Serializa capturas y verificación: nunca dos accesos concurrentes a las imágenes. */
    private val mutex = Mutex()

    private var dniFrontImage: ByteArray? = null
    private var selfieImage: ByteArray? = null

    override suspend fun captureDniFront(claimedDni: String) {
        withContext(dispatcher) {
            delay(CAPTURE_LATENCY_MS)
            mutex.withLock { dniFrontImage = camera.captureDniFront(claimedDni) }
        }
    }

    override suspend fun captureSelfie() {
        withContext(dispatcher) {
            delay(CAPTURE_LATENCY_MS)
            mutex.withLock { selfieImage = camera.captureSelfie() }
        }
    }

    override suspend fun verifyAndDiscard(): EdgeResult = withContext(dispatcher) {
        mutex.withLock {
            val dniImage = dniFrontImage
            val selfie = selfieImage
            try {
                if (dniImage == null || selfie == null) {
                    return@withLock EdgeResult(dni = null, isLive = false)
                }
                delay(ANALYSIS_LATENCY_MS)
                EdgeResult(
                    dni = ocrEngine.extractDni(dniImage),
                    isLive = livenessDetector.isLive(dniImage, selfie),
                )
            } finally {
                // Zero Retention: pase lo que pase (incluida una excepción del motor
                // de OCR), las imágenes no sobreviven a esta llamada.
                releaseImages()
            }
        }
    }

    override fun discardCaptures() {
        releaseImages()
    }

    private fun releaseImages() {
        dniFrontImage = null
        selfieImage = null
    }

    private companion object {
        /** Latencias simuladas para que la UI muestre progreso como con hardware real. */
        const val CAPTURE_LATENCY_MS = 250L
        const val ANALYSIS_LATENCY_MS = 600L
    }
}
