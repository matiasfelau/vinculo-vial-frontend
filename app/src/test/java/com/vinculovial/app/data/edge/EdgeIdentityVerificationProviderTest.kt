package com.vinculovial.app.data.edge

import android.graphics.Bitmap
import com.vinculovial.app.data.edge.face.FaceEmbedder
import com.vinculovial.app.data.edge.face.FaceLocator
import com.vinculovial.app.domain.signup.model.DocumentIdentity
import com.vinculovial.app.domain.signup.model.IdentityVerificationOutcome
import com.vinculovial.app.domain.signup.model.IdentityVerificationRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La estrategia local del patrón: lo que se fija acá es que **falle cerrada**.
 *
 * Cualquier camino por el que no se pueda calcular la similitud tiene que terminar en
 * `Unavailable`, nunca en `Match`: dar por verificada una identidad que no se comparó sería
 * peor que rechazar a alguien legítimo. En JVM `BitmapFactory` devuelve `null` (no hay
 * plataforma Android), así que estos tests recorren justamente los caminos degradados.
 */
class EdgeIdentityVerificationProviderTest {

    @Test
    fun `sin motor de embeddings no se compara nada`() = runTest {
        val provider = provider(embedder = FakeFaceEmbedder(available = false))

        val outcome = provider.verify(request())

        assertEquals(
            IdentityVerificationOutcome.Unavailable(
                IdentityVerificationOutcome.Unavailable.Reason.ENGINE_UNAVAILABLE,
            ),
            outcome,
        )
    }

    @Test
    fun `una imagen que no se puede decodificar no se da por buena`() = runTest {
        val provider = provider(embedder = FakeFaceEmbedder(available = true))

        val outcome = provider.verify(request())

        // Lo determinante no es cuál de los motivos sea, sino que jamás sea un Match.
        assertTrue(outcome is IdentityVerificationOutcome.Unavailable)
    }

    @Test
    fun `no se le pide un embedding a un motor que se declaro no disponible`() = runTest {
        val embedder = FakeFaceEmbedder(available = false)

        provider(embedder = embedder).verify(request())

        assertEquals(0, embedder.embedCalls)
    }

    private fun provider(
        embedder: FakeFaceEmbedder,
        threshold: Float = 0.62f,
    ) = EdgeIdentityVerificationProvider(
        faceLocator = FakeFaceLocator(),
        faceEmbedder = embedder,
        faceMatchThreshold = threshold,
    )

    private fun request() = IdentityVerificationRequest(
        document = DocumentIdentity(number = "12345678", sex = "M", procedureNumber = "00307513148"),
        documentFrontJpeg = ByteArray(32) { 1 },
        selfieJpeg = ByteArray(32) { 2 },
    )

    private class FakeFaceEmbedder(private val available: Boolean) : FaceEmbedder {
        var embedCalls = 0

        override fun isAvailable(): Boolean = available

        override fun embed(faceCrop: Bitmap): FloatArray {
            embedCalls++
            return FloatArray(512) { 0.1f }
        }
    }

    /** Nunca llega a usarse: sin bitmap decodificado no hay nada que recortar. */
    private class FakeFaceLocator : FaceLocator {
        override suspend fun cropDominantFace(image: Bitmap): Bitmap? = null
    }
}
