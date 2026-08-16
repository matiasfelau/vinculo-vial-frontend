package com.vinculovial.app.data.edge

import android.graphics.Bitmap
import com.vinculovial.app.data.edge.document.ArgentineDni
import com.vinculovial.app.data.edge.document.DniBarcodeReader
import com.vinculovial.app.data.edge.document.DocumentFaceDetector
import com.vinculovial.app.di.DefaultDispatcher
import com.vinculovial.app.domain.signup.IdentityVerificationService
import com.vinculovial.app.domain.signup.model.DniFrontQuality
import com.vinculovial.app.domain.signup.model.DocumentIdentity
import com.vinculovial.app.domain.signup.model.EdgeResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Onboarding local: lo único que corre en el dispositivo.
 *
 * **Pivote RENAPER-Ready**: la comparación de caras se movió al backend, que verifica la
 * selfie contra el padrón del Estado. Acá ya no se calcula ningún embedding ni similitud —
 * eso desapareció junto con `TfLiteFaceEmbedder`. Lo que queda del lado del Edge:
 * 1. lectura del código de barras del frente (PDF417 o QR, sin que el usuario elija formato);
 * 2. prueba de vida activa sobre la selfie, que sigue siendo 100% local — es la única
 *    defensa en el dispositivo contra una foto-de-una-foto antes de gastar una llamada de red;
 * 3. preparar la selfie para viajar (redimensionar, comprimir, Base64).
 *
 * Zero Retention sigue aplicando al **documento**: la foto del frente nunca sale de acá,
 * sólo sus campos parseados. Para la **selfie** ya no aplica — [SelfieCompressor] produce
 * deliberadamente el único byte de imagen que cruza la red en todo US-01. Ver `CLAUDE.md`.
 */
@Singleton
class EdgeIdentityVerificationService @Inject constructor(
    private val barcodeReader: DniBarcodeReader,
    private val faceDetector: DocumentFaceDetector,
    private val selfieCompressor: SelfieCompressor,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : IdentityVerificationService {

    /** Serializa capturas y verificación: nunca dos accesos concurrentes a las imágenes. */
    private val mutex = Mutex()

    private var dniFront: SecureImageBuffer? = null
    private var selfie: SecureImageBuffer? = null
    private var livenessPassed = false

    override suspend fun acceptDniFront(image: ByteArray) = store(image) { captured ->
        dniFront?.clear()
        dniFront = captured
    }

    override suspend fun canReadDniFront(image: ByteArray): DniFrontQuality = withContext(dispatcher) {
        // copyOf, no wrap: a diferencia de acceptDniFront, acá NO se toma posesión de
        // `image` — el llamador todavía necesita el array intacto para, si esto da bien,
        // pasarlo a acceptDniFront. wrap() lo hubiera dejado en ceros al liberarlo acá.
        val buffer = SecureImageBuffer.copyOf(image)
        try {
            buffer.readBitmap { bitmap ->
                when {
                    bitmap == null -> DniFrontQuality.BARCODE_UNREADABLE
                    barcodeReader.readDni(bitmap) == null -> DniFrontQuality.BARCODE_UNREADABLE
                    !faceDetector.hasFace(bitmap) -> DniFrontQuality.PHOTO_NOT_FOUND
                    else -> DniFrontQuality.READABLE
                }
            }
        } finally {
            buffer.clear()
        }
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
                // Zero Retention para el documento y para la selfie ORIGINAL: pase lo que
                // pase, ninguna de las dos sobrevive a esta llamada. Lo único que sale de
                // acá con vida es el string ya comprimido que devuelve verify(), y ese es
                // exactamente el que está diseñado para viajar.
                releaseImages()
            }
        }
    }

    override fun discardCaptures() = releaseImages()

    private suspend fun verify(): EdgeResult {
        val frontBuffer = dniFront
        val selfieBuffer = selfie

        if (frontBuffer == null || selfieBuffer == null) {
            return EdgeResult.Rejected(EdgeResult.Reason.INCOMPLETE_CAPTURE)
        }

        // La prueba de vida se corrió sobre el preview; si no se superó, no hay nada que
        // mandar: una foto de una foto no debe llegar ni siquiera al backend.
        if (!livenessPassed) {
            return EdgeResult.Rejected(EdgeResult.Reason.LIVENESS_FAILED)
        }

        // Lectura independiente de la de canReadDniFront(): esa fue sólo una consulta rápida
        // para la UX ("¿sirve esta foto?"), esta es la única fuente de verdad para lo que
        // efectivamente se manda.
        val document = frontBuffer.readBitmap { bitmap ->
            bitmap?.let { barcodeReader.readDni(it) }
        } ?: return EdgeResult.Rejected(EdgeResult.Reason.BARCODE_NOT_FOUND)

        val procedureNumber = document.procedureNumber
            ?: return EdgeResult.Rejected(EdgeResult.Reason.MISSING_PROCEDURE_NUMBER)

        val selfieBase64 = selfieBuffer.readSuspending { jpeg -> selfieCompressor.compress(jpeg) }
            ?: return EdgeResult.Rejected(EdgeResult.Reason.INCOMPLETE_CAPTURE)

        return EdgeResult.Verified(
            document = document.toDomain(procedureNumber),
            selfieBase64 = selfieBase64,
        )
    }

    private fun ArgentineDni.toDomain(procedureNumber: String) = DocumentIdentity(
        number = number,
        sex = sex,
        procedureNumber = procedureNumber,
    )

    /**
     * Decodifica el JPEG del buffer, ejecuta [block] y borra el bitmap después.
     * Los píxeles descomprimidos son una segunda copia de la cara y viven lo mínimo.
     */
    private suspend fun <R> SecureImageBuffer.readBitmap(block: suspend (Bitmap?) -> R): R {
        var bitmap: Bitmap? = null
        return try {
            bitmap = readSuspending { jpeg -> SecureBitmap.decode(jpeg, DOCUMENT_DECODE_DIMENSION) }
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
        dniFront?.clear()
        selfie?.clear()
        dniFront = null
        selfie = null
        livenessPassed = false
    }

    private companion object {
        /**
         * Piso de resolución al decodificar el documento. Lo dicta el PDF417, no la cara: un
         * decodificador de códigos de barras necesita del orden de 2-3 píxeles por módulo, y
         * el código ocupa cerca de la mitad del ancho de la tarjeta.
         */
        const val DOCUMENT_DECODE_DIMENSION = 3_000
    }
}
