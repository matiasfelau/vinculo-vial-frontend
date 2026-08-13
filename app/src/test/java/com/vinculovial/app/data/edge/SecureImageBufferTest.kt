package com.vinculovial.app.data.edge

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureImageBufferTest {

    @Test
    fun `presta los bytes mientras esta vivo`() {
        val buffer = SecureImageBuffer.wrap(byteArrayOf(1, 2, 3))

        val leido = buffer.read { it.toList() }

        assertEquals(listOf<Byte>(1, 2, 3), leido)
        assertFalse(buffer.isCleared)
    }

    @Test
    fun `al liberar sobreescribe el contenido con ceros`() {
        val bytes = byteArrayOf(9, 8, 7, 6)
        val buffer = SecureImageBuffer.wrap(bytes)

        buffer.clear()

        assertArrayEquals(ByteArray(4), bytes)
        assertTrue(buffer.isCleared)
    }

    @Test
    fun `liberar es idempotente`() {
        val buffer = SecureImageBuffer.wrap(byteArrayOf(1, 2))

        buffer.clear()
        buffer.clear()

        assertTrue(buffer.isCleared)
    }

    @Test
    fun `leer despues de liberar falla`() {
        val buffer = SecureImageBuffer.wrap(byteArrayOf(1, 2))
        buffer.clear()

        assertThrows(IllegalStateException::class.java) { buffer.read { it.size } }
    }

    @Test
    fun `readAndClear libera aunque el bloque falle`() {
        val bytes = byteArrayOf(5, 5, 5)
        val buffer = SecureImageBuffer.wrap(bytes)

        runCatching { buffer.readAndClear { error("motor caído") } }

        assertTrue(buffer.isCleared)
        assertArrayEquals(ByteArray(3), bytes)
    }

    @Test
    fun `close permite usarlo con use`() {
        val bytes = byteArrayOf(4, 4)

        SecureImageBuffer.wrap(bytes).use { it.read { content -> content.size } }

        assertArrayEquals(ByteArray(2), bytes)
    }

    @Test
    fun `copyOf no deja que la fuente comparta el contenido`() {
        val source = byteArrayOf(1, 2, 3)
        val buffer = SecureImageBuffer.copyOf(source)

        buffer.clear()

        // La fuente sigue intacta: el buffer trabajó sobre su propia copia.
        assertArrayEquals(byteArrayOf(1, 2, 3), source)
    }

    @Test
    fun `no quedan referencias vivas al contenido despues de liberar`() {
        val buffer = SecureImageBuffer.wrap(ByteArray(1_024) { it.toByte() })

        buffer.clear()

        // Verificación directa del campo interno: tras liberar, el buffer no sostiene
        // ninguna referencia a la imagen (queda elegible para GC de inmediato).
        val field = SecureImageBuffer::class.java.getDeclaredField("bytes").apply { isAccessible = true }
        assertNull("el buffer siguió reteniendo la imagen", field.get(buffer))
        assertTrue(buffer.isCleared)
    }

    @Test
    fun `toString no expone el contenido`() {
        val buffer = SecureImageBuffer.wrap(byteArrayOf(42, 42, 42))

        assertEquals("SecureImageBuffer(originalSize=3, cleared=false)", buffer.toString())
    }
}
