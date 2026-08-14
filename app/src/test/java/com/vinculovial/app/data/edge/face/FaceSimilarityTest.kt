package com.vinculovial.app.data.edge.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class FaceSimilarityTest {

    @Test
    fun `un vector normalizado tiene norma uno`() {
        val normalized = floatArrayOf(3f, 4f, 0f, 0f).l2Normalized()

        val norm = sqrt(normalized.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1f, norm, 1e-5f)
    }

    @Test
    fun `normalizar un vector nulo no divide por cero`() {
        val normalized = floatArrayOf(0f, 0f, 0f).l2Normalized()

        assertTrue(normalized.all { it == 0f })
    }

    @Test
    fun `el mismo rostro da similitud uno`() {
        val embedding = floatArrayOf(0.2f, -0.5f, 0.8f, 0.1f).l2Normalized()

        assertEquals(1f, cosineSimilarity(embedding, embedding), 1e-5f)
    }

    @Test
    fun `vectores opuestos dan menos uno`() {
        val embedding = floatArrayOf(0.6f, 0.8f).l2Normalized()
        val opposite = floatArrayOf(-0.6f, -0.8f).l2Normalized()

        assertEquals(-1f, cosineSimilarity(embedding, opposite), 1e-5f)
    }

    @Test
    fun `vectores ortogonales dan cero`() {
        val first = floatArrayOf(1f, 0f).l2Normalized()
        val second = floatArrayOf(0f, 1f).l2Normalized()

        assertEquals(0f, cosineSimilarity(first, second), 1e-5f)
    }

    @Test
    fun `la similitud nunca se sale del rango por error de redondeo`() {
        val embedding = floatArrayOf(0.577f, 0.577f, 0.577f).l2Normalized()

        val similarity = cosineSimilarity(embedding, embedding)

        assertTrue("similitud fuera de rango: $similarity", similarity in -1f..1f)
    }

    @Test
    fun `comparar embeddings de distinto tamano es un error de programacion`() {
        assertThrows(IllegalArgumentException::class.java) {
            cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f, 0f))
        }
    }
}
