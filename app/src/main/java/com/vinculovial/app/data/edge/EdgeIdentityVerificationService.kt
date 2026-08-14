package com.vinculovial.app.data.edge

import android.graphics.Bitmap
import com.vinculovial.app.data.edge.document.DniBarcodeReader
import com.vinculovial.app.data.edge.face.FaceEmbedder
import com.vinculovial.app.data.edge.face.FaceEmbedderUnavailableException
import com.vinculovial.app.data.edge.face.FaceLocator
import com.vinculovial.app.data.edge.face.cosineSimilarity
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
 * Onboarding biométrico completo, 100 % en el dispositivo.
 *
 * Encadena las tres verificaciones y garantiza que ninguna imagen sobreviva:
 * 1. PDF417 del reverso → número de documento;
 * 2. cara del frente del documento → embedding A;
 * 3. cara de la selfie (con prueba de vida ya superada) → embedding B;
 * 4. `coseno(A, B) ≥ umbral` → identidad verificada.
 *
 * Hacia afuera sólo viaja [EdgeResult]. Ni las fotos ni los embeddings salen de acá: un
 * embedding es un dato biométrico y tratarlo como si fuera inocuo sería tan grave como
 * mandar la foto.
 */
@Singleton
class EdgeIdentityVerificationService @Inject constructor(
    private val barcodeReader: DniBarcodeReader,
    private val faceLocator: FaceLocator,
    private val faceEmbedder: FaceEmbedder,
    private val faceMatchThreshold: Float,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : IdentityVerificationService {

    /** Serializa capturas y verificación: nunca dos accesos concurrentes a las imágenes. */
    private val mutex = Mutex()

    private var dniBack: SecureImageBuffer? = null
    private var dniFront: SecureImageBuffer? = null
    private var selfie: SecureImageBuffer? = null
    private var livenessPassed = false

    override suspend fun acceptDniBack(image: ByteArray) = store(image) { previous ->
        dniBack?.clear()
        dniBack = previous
    }

    override suspend fun acceptDniFront(image: ByteArray) = store(image) { captured ->
        dniFront?.clear()
        dniFront = captured
    }

    override suspend fun acceptSelfie(image: ByteArray, livenessPassed: Boolean) {
        store(image) { captured ->
            selfie?.clear()
            selfie = captured
            this.livenessPassed = livenessPassed
        }
    }

    override suspend fun verifyAndDiscard(): EdgeResult = withContext(dispatcher) {
        mutex.withLock {
            try {
                verify()
            } finally {
                // Zero Retention: pase lo que pase (incluida una excepción de cualquier
                // motor), las imágenes no sobreviven a esta llamada.
                releaseImages()
            }
        }
    }

    override fun discardCaptures() = releaseImages()

    private suspend fun verify(): EdgeResult {
        val backBuffer = dniBack
        val frontBuffer = dniFront
        val selfieBuffer = selfie

        if (backBuffer == null || frontBuffer == null || selfieBuffer == null) {
            return EdgeResult.Rejected(EdgeResult.Reason.INCOMPLETE_CAPTURE)
        }

        // La prueba de vida se corrió sobre el preview; si no se superó, no hay nada que
        // comparar: una foto de una foto no debe llegar al match facial.
        if (!livenessPassed) {
            return EdgeResult.Rejected(EdgeResult.Reason.LIVENESS_FAILED)
        }

        // Si falta el modelo se corta acá, antes de gastar el resto del pipeline. No
        // poder calcular el match nunca equivale a que el match haya dado bien.
        if (!faceEmbedder.isAvailable()) {
            return EdgeResult.Rejected(EdgeResult.Reason.BIOMETRICS_UNAVAILABLE)
        }

        val dniNumber = backBuffer.readBitmap { bitmap ->
            bitmap?.let { barcodeReader.readDniNumber(it) }
        } ?: return EdgeResult.Rejected(EdgeResult.Reason.BARCODE_NOT_FOUND)

        val documentEmbedding = frontBuffer.readBitmap { bitmap ->
            bitmap?.let { embedFace(it) }
        } ?: return EdgeResult.Rejected(EdgeResult.Reason.NO_FACE_IN_DOCUMENT)

        val selfieEmbedding = selfieBuffer.readBitmap { bitmap ->
            bitmap?.let { embedFace(it) }
        } ?: return EdgeResult.Rejected(EdgeResult.Reason.NO_FACE_IN_SELFIE)

        val similarity = try {
            cosineSimilarity(documentEmbedding, selfieEmbedding)
        } catch (e: IllegalArgumentException) {
            return EdgeResult.Rejected(EdgeResult.Reason.BIOMETRICS_UNAVAILABLE)
        } finally {
            // Los embeddings son datos biométricos: se borran igual que las imágenes.
            documentEmbedding.fill(0f)
            selfieEmbedding.fill(0f)
        }

        return if (similarity >= faceMatchThreshold) {
            EdgeResult.Verified(dni = dniNumber, faceSimilarity = similarity)
        } else {
            EdgeResult.Rejected(EdgeResult.Reason.FACE_MISMATCH)
        }
    }

    /** `null` si no hay cara utilizable o si el motor no está disponible. */
    private suspend fun embedFace(image: Bitmap): FloatArray? {
        val crop = faceLocator.cropDominantFace(image) ?: return null
        return try {
            faceEmbedder.embed(crop)
        } catch (e: FaceEmbedderUnavailableException) {
            null
        } finally {
            SecureBitmap.wipe(crop)
        }
    }

    /**
     * Decodifica el JPEG del buffer, ejecuta [block] y borra el bitmap después.
     * Los píxeles descomprimidos son una segunda copia de la cara y viven lo mínimo.
     */
    private suspend fun <R> SecureImageBuffer.readBitmap(block: suspend (Bitmap?) -> R): R {
        var bitmap: Bitmap? = null
        return try {
            bitmap = read { jpeg -> SecureBitmap.decode(jpeg) }
            block(bitmap)
        } finally {
            SecureBitmap.wipe(bitmap)
        }
    }

    private suspend fun store(image: ByteArray, assign: (SecureImageBuffer) -> Unit) {
        // wrap() toma posesión de los bytes: la única referencia viva queda dentro del
        // buffer, que se sabe borrar.
        val captured = SecureImageBuffer.wrap(image)
        withContext(dispatcher) {
            mutex.withLock { assign(captured) }
        }
    }

    private fun releaseImages() {
        dniBack?.clear()
        dniFront?.clear()
        selfie?.clear()
        dniBack = null
        dniFront = null
        selfie = null
        livenessPassed = false
    }
}
