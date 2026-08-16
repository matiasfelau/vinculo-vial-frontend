package com.vinculovial.app.data.edge

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.vinculovial.app.BuildConfig
import com.vinculovial.app.data.edge.face.FaceEmbedder
import com.vinculovial.app.data.edge.face.FaceEmbedderUnavailableException
import com.vinculovial.app.data.edge.face.FaceLocator
import com.vinculovial.app.data.edge.face.FaceSource
import com.vinculovial.app.data.edge.face.cosineSimilarity
import com.vinculovial.app.domain.signup.IdentityVerificationProvider
import com.vinculovial.app.domain.signup.model.IdentityVerificationOutcome
import com.vinculovial.app.domain.signup.model.IdentityVerificationRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verificación de identidad 100 % en el dispositivo: recorta las dos caras con ML Kit,
 * produce sus embeddings con TensorFlow Lite y las compara por similitud coseno.
 *
 * Es la implementación activa del onboarding. Nada sale del teléfono: ni las imágenes ni los
 * embeddings, que son datos biométricos por derecho propio y se ponen en cero apenas se usan.
 *
 * Los datos del documento ([IdentityVerificationRequest.document]) no se usan acá — la
 * comparación es puramente cara contra cara. Están en el contrato porque la estrategia
 * alternativa, consultar al padrón del Estado, los necesita para identificar contra qué
 * registro comparar.
 */
@Singleton
class EdgeIdentityVerificationProvider @Inject constructor(
    private val faceLocator: FaceLocator,
    private val faceEmbedder: FaceEmbedder,
    private val faceMatchThreshold: Float,
) : IdentityVerificationProvider {

    override suspend fun verify(request: IdentityVerificationRequest): IdentityVerificationOutcome {
        // Si falta el modelo se corta antes de gastar el resto del pipeline. No poder
        // calcular el match nunca equivale a que el match haya dado bien.
        if (!faceEmbedder.isAvailable()) {
            trace("motor no disponible: el modelo no se pudo cargar (ver el tag TfLiteFaceEmbedder)")
            return unavailable(IdentityVerificationOutcome.Unavailable.Reason.ENGINE_UNAVAILABLE)
        }

        val documentEmbeddings = embedFaces(request.documentFrontJpeg, FaceSource.DOCUMENT)
        if (documentEmbeddings.isEmpty()) {
            trace("no se encontró una cara utilizable en la foto del documento")
            return unavailable(IdentityVerificationOutcome.Unavailable.Reason.NO_FACE_IN_DOCUMENT)
        }

        val selfieEmbeddings = embedFaces(request.selfieJpeg, FaceSource.SELFIE)
        if (selfieEmbeddings.isEmpty()) {
            documentEmbeddings.wipe()
            trace("no se encontró una cara utilizable en la selfie")
            return unavailable(IdentityVerificationOutcome.Unavailable.Reason.NO_FACE_IN_SELFIE)
        }

        val similarity = try {
            bestSimilarity(documentEmbeddings, selfieEmbeddings)
        } catch (e: IllegalArgumentException) {
            // Embeddings de distinta dimensión: el modelo cargado no es el que se espera.
            trace("embeddings incompatibles: ${e.message}")
            return unavailable(IdentityVerificationOutcome.Unavailable.Reason.ENGINE_UNAVAILABLE)
        } finally {
            documentEmbeddings.wipe()
            selfieEmbeddings.wipe()
        }

        val matched = similarity >= faceMatchThreshold
        trace("Cosine Similarity: $similarity (umbral $faceMatchThreshold) -> ${if (matched) "MATCH" else "NO MATCH"}")

        return if (matched) {
            IdentityVerificationOutcome.Match(similarity)
        } else {
            IdentityVerificationOutcome.NoMatch(similarity)
        }
    }

    /**
     * Mejor similitud entre todas las combinaciones, que en la práctica es "la selfie tal
     * cual" contra "la selfie espejada".
     *
     * MobileFaceNet **no** es invariante al espejado horizontal: comparar una cara contra su
     * reflejo baja la similitud de forma perfectamente capaz de cruzar el umbral hacia abajo.
     * Y si la selfie está espejada o no depende del OEM: CameraX no espeja el JPEG de la
     * cámara frontal, pero el preview sí se muestra espejado y algunos HAL entregan el still
     * ya invertido (por eso existe `ImageCapture.Metadata.isReversedHorizontal`). Probar las
     * dos orientaciones cuesta una inferencia más —milisegundos— y elimina de raíz una clase
     * entera de falso negativo que depende del teléfono.
     *
     * Tomar el máximo no afloja la seguridad: dos personas distintas no se parecen más
     * porque una esté espejada.
     */
    private fun bestSimilarity(
        documentEmbeddings: List<FloatArray>,
        selfieEmbeddings: List<FloatArray>,
    ): Float {
        var best = -1f
        for ((documentIndex, document) in documentEmbeddings.withIndex()) {
            for ((selfieIndex, selfie) in selfieEmbeddings.withIndex()) {
                val score = cosineSimilarity(document, selfie)
                trace("  documento[$documentIndex] vs selfie[$selfieIndex] = $score")
                if (score > best) best = score
            }
        }
        return best
    }

    /**
     * Embeddings de la cara dominante: el recorte tal cual y, para la selfie, también su
     * espejo. Lista vacía si no hay cara utilizable o el motor no pudo con ella.
     */
    private suspend fun embedFaces(jpeg: ByteArray, source: FaceSource): List<FloatArray> {
        var image: Bitmap? = null
        var crop: Bitmap? = null
        return try {
            image = SecureBitmap.decode(jpeg, source.decodeDimension()) ?: return emptyList()
            crop = faceLocator.cropDominantFace(image, source) ?: return emptyList()
            trace("$source: imagen ${image.width}x${image.height}, recorte ${crop.width}x${crop.height}")

            val embeddings = mutableListOf<FloatArray>()
            embeddings += embedOrNull(crop) ?: return emptyList()

            if (source == FaceSource.SELFIE) {
                crop.mirrored()?.let { mirror ->
                    try {
                        embedOrNull(mirror)?.let { embeddings += it }
                    } finally {
                        SecureBitmap.wipe(mirror)
                    }
                }
            }
            embeddings
        } finally {
            // Los píxeles descomprimidos son otra copia de la cara: viven lo mínimo.
            SecureBitmap.wipe(crop)
            SecureBitmap.wipe(image)
        }
    }

    private fun embedOrNull(faceCrop: Bitmap): FloatArray? = try {
        faceEmbedder.embed(faceCrop)
    } catch (e: FaceEmbedderUnavailableException) {
        trace("el motor rechazó el recorte: ${e.message}")
        null
    }

    private fun Bitmap.mirrored(): Bitmap? = runCatching {
        Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { preScale(-1f, 1f) }, true)
    }.getOrNull()

    /** Los embeddings son datos biométricos: se borran igual que las imágenes. */
    private fun List<FloatArray>.wipe() = forEach { it.fill(0f) }

    private fun unavailable(reason: IdentityVerificationOutcome.Unavailable.Reason) =
        IdentityVerificationOutcome.Unavailable(reason)

    /**
     * Traza de calibración, sólo en builds de debug.
     *
     * El umbral (`FACE_MATCH_THRESHOLD`) está sin calibrar y comparar una foto **impresa** de
     * DNI contra una selfie da similitudes sistemáticamente más bajas que comparar dos
     * selfies; sin ver el número real no hay forma de distinguir "el umbral está alto" de "el
     * pipeline está roto". Se registran el score y las dimensiones de los recortes, nunca los
     * embeddings ni el número de documento: un vector facial es un dato biométrico y un log
     * es un lugar donde sobrevive.
     */
    private fun trace(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    /**
     * Cuánta resolución necesita cada punta, que no es la misma.
     *
     * En una selfie la cara ocupa medio cuadro: sobra con una imagen moderada, y decodificar
     * menos es más rápido y menos memoria. En el documento la cara impresa puede ser un 4% del
     * ancho si el DNI se sostiene a una distancia cómoda — a resolución baja ese recorte
     * llegaría al embedder con demasiados pocos píxeles y el embedding perdería calidad justo
     * en el caso que queremos soportar.
     */
    private fun FaceSource.decodeDimension(): Int = when (this) {
        FaceSource.DOCUMENT -> DOCUMENT_DECODE_DIMENSION
        FaceSource.SELFIE -> SELFIE_DECODE_DIMENSION
    }

    private companion object {
        const val TAG = "BIOMETRY"
        const val DOCUMENT_DECODE_DIMENSION = 3_000
        const val SELFIE_DECODE_DIMENSION = 1_600
    }
}
