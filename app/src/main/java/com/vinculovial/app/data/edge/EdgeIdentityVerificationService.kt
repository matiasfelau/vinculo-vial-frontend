package com.vinculovial.app.data.edge

import android.graphics.Bitmap
import com.vinculovial.app.data.edge.document.ArgentineDni
import com.vinculovial.app.data.edge.document.DniBarcodeReader
import com.vinculovial.app.data.edge.face.FaceLocator
import com.vinculovial.app.data.edge.face.FaceSource
import com.vinculovial.app.di.DefaultDispatcher
import com.vinculovial.app.domain.signup.IdentityVerificationProvider
import com.vinculovial.app.domain.signup.IdentityVerificationService
import com.vinculovial.app.domain.signup.model.DniFrontQuality
import com.vinculovial.app.domain.signup.model.DocumentIdentity
import com.vinculovial.app.domain.signup.model.EdgeResult
import com.vinculovial.app.domain.signup.model.IdentityVerificationOutcome
import com.vinculovial.app.domain.signup.model.IdentityVerificationRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sesión de captura del onboarding biométrico: sostiene las imágenes, lee el documento y
 * garantiza que nada sobreviva.
 *
 * Lo que **no** hace es decidir si las dos caras son de la misma persona: eso lo resuelve el
 * [IdentityVerificationProvider] inyectado, que hoy es el local
 * ([EdgeIdentityVerificationProvider], TensorFlow Lite sobre el dispositivo) y mañana podría
 * ser el padrón del Estado. Esa separación es deliberada: el Zero Retention vive acá, en un
 * único lugar común a todas las estrategias, para que agregar una estrategia nueva no sea
 * también una oportunidad nueva de filtrar las imágenes.
 *
 * El orden de las verificaciones es de más barato a más caro, y cada paso es una barrera:
 * 1. están las dos capturas;
 * 2. la prueba de vida se superó — una foto de una foto no debe llegar al match facial;
 * 3. el código de barras da un documento;
 * 4. recién ahí se paga la comparación biométrica.
 */
@Singleton
class EdgeIdentityVerificationService @Inject constructor(
    private val barcodeReader: DniBarcodeReader,
    private val faceLocator: FaceLocator,
    private val verificationProvider: IdentityVerificationProvider,
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
                    !hasDetectableFace(bitmap) -> DniFrontQuality.PHOTO_NOT_FOUND
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
                // Zero Retention: pase lo que pase (incluida una excepción de un motor o de
                // un provider no implementado), las imágenes no sobreviven a esta llamada.
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
        // comparar: una foto de una foto no debe llegar al match facial.
        if (!livenessPassed) {
            return EdgeResult.Rejected(EdgeResult.Reason.LIVENESS_FAILED)
        }

        val document = frontBuffer.readBitmap { bitmap ->
            bitmap?.let { barcodeReader.readDni(it) }
        } ?: return EdgeResult.Rejected(EdgeResult.Reason.BARCODE_NOT_FOUND)

        // Los bytes se prestan anidados, no se copian: las únicas referencias vivas siguen
        // siendo las de los buffers, que se borran en el `finally` de verifyAndDiscard().
        val outcome = frontBuffer.readSuspending { frontJpeg ->
            selfieBuffer.readSuspending { selfieJpeg ->
                verificationProvider.verify(
                    IdentityVerificationRequest(
                        document = document.toDomain(),
                        documentFrontJpeg = frontJpeg,
                        selfieJpeg = selfieJpeg,
                    ),
                )
            }
        }

        return outcome.toEdgeResult(document.number)
    }

    private fun ArgentineDni.toDomain() = DocumentIdentity(
        number = number,
        sex = sex,
        procedureNumber = procedureNumber,
    )

    private fun IdentityVerificationOutcome.toEdgeResult(dniNumber: String): EdgeResult = when (this) {
        is IdentityVerificationOutcome.Match ->
            EdgeResult.Verified(dni = dniNumber, faceSimilarity = confidence)

        is IdentityVerificationOutcome.NoMatch ->
            EdgeResult.Rejected(EdgeResult.Reason.FACE_MISMATCH)

        is IdentityVerificationOutcome.Unavailable -> EdgeResult.Rejected(
            when (reason) {
                IdentityVerificationOutcome.Unavailable.Reason.NO_FACE_IN_DOCUMENT ->
                    EdgeResult.Reason.NO_FACE_IN_DOCUMENT

                IdentityVerificationOutcome.Unavailable.Reason.NO_FACE_IN_SELFIE ->
                    EdgeResult.Reason.NO_FACE_IN_SELFIE

                IdentityVerificationOutcome.Unavailable.Reason.ENGINE_UNAVAILABLE ->
                    EdgeResult.Reason.BIOMETRICS_UNAVAILABLE
            },
        )
    }

    /**
     * Sólo presencia, no identidad: para [canReadDniFront] alcanza con saber que hay una
     * cara recortable. El embedding (caro, y sin nada todavía para compararlo) se calcula
     * recién al verificar, cuando también existe la selfie.
     */
    private suspend fun hasDetectableFace(image: Bitmap): Boolean {
        val crop = faceLocator.cropDominantFace(image, FaceSource.DOCUMENT) ?: return false
        SecureBitmap.wipe(crop)
        return true
    }

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
         *
         * Tiene que acompañar al techo de la captura (`MAX_CAPTURE_DIMENSION_PX`): si acá se
         * volviera a submuestrear, se tiraría exactamente la resolución que la captura acaba
         * de conservar para permitir leer el documento a una distancia cómoda.
         */
        const val DOCUMENT_DECODE_DIMENSION = 3_000
    }
}
