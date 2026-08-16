package com.vinculovial.app.data.edge

import android.graphics.Bitmap
import com.vinculovial.app.data.edge.face.FaceEmbedder
import com.vinculovial.app.data.edge.face.FaceEmbedderUnavailableException
import com.vinculovial.app.data.edge.face.FaceLocator
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
            return unavailable(IdentityVerificationOutcome.Unavailable.Reason.ENGINE_UNAVAILABLE)
        }

        val documentEmbedding = embedFaceIn(request.documentFrontJpeg)
            ?: return unavailable(IdentityVerificationOutcome.Unavailable.Reason.NO_FACE_IN_DOCUMENT)

        val selfieEmbedding = embedFaceIn(request.selfieJpeg)
            ?: run {
                documentEmbedding.fill(0f)
                return unavailable(IdentityVerificationOutcome.Unavailable.Reason.NO_FACE_IN_SELFIE)
            }

        val similarity = try {
            cosineSimilarity(documentEmbedding, selfieEmbedding)
        } catch (e: IllegalArgumentException) {
            // Embeddings de distinta dimensión: el modelo cargado no es el que se espera.
            return unavailable(IdentityVerificationOutcome.Unavailable.Reason.ENGINE_UNAVAILABLE)
        } finally {
            documentEmbedding.fill(0f)
            selfieEmbedding.fill(0f)
        }

        return if (similarity >= faceMatchThreshold) {
            IdentityVerificationOutcome.Match(similarity)
        } else {
            IdentityVerificationOutcome.NoMatch(similarity)
        }
    }

    /** `null` si no hay cara utilizable en la imagen, o si el motor no pudo con ella. */
    private suspend fun embedFaceIn(jpeg: ByteArray): FloatArray? {
        var image: Bitmap? = null
        return try {
            image = SecureBitmap.decode(jpeg) ?: return null
            val crop = faceLocator.cropDominantFace(image) ?: return null
            try {
                faceEmbedder.embed(crop)
            } catch (e: FaceEmbedderUnavailableException) {
                null
            } finally {
                SecureBitmap.wipe(crop)
            }
        } finally {
            // Los píxeles descomprimidos son otra copia de la cara: viven lo mínimo.
            SecureBitmap.wipe(image)
        }
    }

    private fun unavailable(reason: IdentityVerificationOutcome.Unavailable.Reason) =
        IdentityVerificationOutcome.Unavailable(reason)
}
