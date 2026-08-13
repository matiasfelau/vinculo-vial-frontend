package com.vinculovial.app.data.edge

/**
 * Buffer de imagen de vida corta y final explícito (Zero Retention).
 *
 * Reglas:
 * - toma posesión de los bytes: quien lo crea no debe conservar la referencia original;
 * - presta los bytes sólo dentro de [read], nunca los devuelve;
 * - al liberarlo sobreescribe el contenido con ceros y suelta la referencia, para que el
 *   contenido no quede legible en el heap hasta que pase el GC.
 *
 * No hay copias en disco ni cache: la imagen existe únicamente en este objeto.
 */
class SecureImageBuffer private constructor(
    private var bytes: ByteArray?,
    /** Tamaño original, útil para métricas/logs sin tocar el contenido. */
    val originalSize: Int,
) : AutoCloseable {

    val isCleared: Boolean get() = bytes == null

    /**
     * Presta los bytes al [block]. El bloque no debe guardar la referencia recibida.
     *
     * @throws IllegalStateException si el buffer ya fue liberado.
     */
    fun <R> read(block: (ByteArray) -> R): R {
        val current = checkNotNull(bytes) { "El buffer de imagen ya fue liberado" }
        return block(current)
    }

    /** Presta los bytes y libera el buffer inmediatamente después, incluso si falla. */
    fun <R> readAndClear(block: (ByteArray) -> R): R = try {
        read(block)
    } finally {
        clear()
    }

    /** Sobreescribe con ceros y suelta la referencia. Idempotente. */
    fun clear() {
        bytes?.fill(ZERO)
        bytes = null
    }

    override fun close() = clear()

    /** Nunca renderizar el contenido. */
    override fun toString(): String =
        "SecureImageBuffer(originalSize=$originalSize, cleared=$isCleared)"

    companion object {
        private const val ZERO: Byte = 0

        /** Toma posesión de [bytes]; el llamador debe olvidar su referencia. */
        fun wrap(bytes: ByteArray): SecureImageBuffer = SecureImageBuffer(bytes, bytes.size)

        /** Copia defensiva, para fuentes que reutilizan su propio buffer (p. ej. la cámara). */
        fun copyOf(source: ByteArray): SecureImageBuffer = wrap(source.copyOf())
    }
}
