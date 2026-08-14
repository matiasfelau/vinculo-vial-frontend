package com.vinculovial.app.data.edge.face

import android.graphics.Bitmap

/**
 * Convierte una cara recortada en un vector de características (embedding).
 *
 * El vector viene L2-normalizado, así que la similitud coseno entre dos embeddings es
 * simplemente su producto punto.
 */
interface FaceEmbedder {

    /** @throws FaceEmbedderUnavailableException si el motor no está disponible. */
    fun embed(faceCrop: Bitmap): FloatArray

    /** `false` si falta el modelo: permite fallar temprano y con un mensaje claro. */
    fun isAvailable(): Boolean
}

/**
 * El motor de reconocimiento facial no se pudo inicializar.
 *
 * En la práctica significa que falta el modelo `.tflite` en `assets/`. Es un error de
 * build, no del usuario: nunca debe traducirse en "dar por válida" la comparación.
 */
class FaceEmbedderUnavailableException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/** Similitud coseno entre dos embeddings L2-normalizados: producto punto, en `[-1, 1]`. */
internal fun cosineSimilarity(first: FloatArray, second: FloatArray): Float {
    require(first.size == second.size) {
        "Embeddings de distinto tamaño: ${first.size} vs ${second.size}"
    }
    var dot = 0f
    for (i in first.indices) {
        dot += first[i] * second[i]
    }
    return dot.coerceIn(-1f, 1f)
}

/** Deja el vector con norma 1 para que la similitud coseno sea el producto punto. */
internal fun FloatArray.l2Normalized(): FloatArray {
    var sumOfSquares = 0f
    for (value in this) {
        sumOfSquares += value * value
    }
    val norm = kotlin.math.sqrt(sumOfSquares)
    if (norm <= 0f) return this
    return FloatArray(size) { this[it] / norm }
}
