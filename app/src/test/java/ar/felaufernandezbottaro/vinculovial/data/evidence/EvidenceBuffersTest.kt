package ar.felaufernandezbottaro.vinculovial.data.evidence

import ar.felaufernandezbottaro.vinculovial.data.edge.SecureImageBuffer
import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidenceRole
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceBuffersTest {

    private fun photo(vararg bytes: Byte) = bytes.copyOf()

    @Test
    fun `guarda y presta la foto de un casillero`() {
        val buffers = EvidenceBuffers()
        buffers.put(EvidenceRole.GENERAL, 0, SecureImageBuffer.wrap(photo(1, 2, 3)))

        assertEquals(listOf<Byte>(1, 2, 3), buffers.read(EvidenceRole.GENERAL, 0) { it.toList() })
        assertTrue(buffers.has(EvidenceRole.GENERAL, 0))
    }

    @Test
    fun `un casillero vacio devuelve null`() {
        assertNull(EvidenceBuffers().read(EvidenceRole.PLATE, 0) { it.size })
    }

    @Test
    fun `reemplazar un casillero sobreescribe con ceros la foto anterior`() {
        val buffers = EvidenceBuffers()
        val previous = photo(5, 5, 5)
        buffers.put(EvidenceRole.PLATE, 0, SecureImageBuffer.wrap(previous))

        buffers.put(EvidenceRole.PLATE, 0, SecureImageBuffer.wrap(photo(9, 9)))

        assertArrayEquals(ByteArray(3), previous)
        assertEquals(1, buffers.size)
    }

    @Test
    fun `quitar un casillero sobreescribe con ceros la foto`() {
        val buffers = EvidenceBuffers()
        val bytes = photo(7, 7)
        buffers.put(EvidenceRole.GENERAL, 0, SecureImageBuffer.wrap(bytes))

        buffers.remove(EvidenceRole.GENERAL, 0)

        assertArrayEquals(ByteArray(2), bytes)
        assertFalse(buffers.has(EvidenceRole.GENERAL, 0))
    }

    @Test
    fun `clear sobreescribe con ceros todas las fotos y es idempotente`() {
        val buffers = EvidenceBuffers()
        val all = listOf(photo(1), photo(2), photo(3))
        buffers.put(EvidenceRole.GENERAL, 0, SecureImageBuffer.wrap(all[0]))
        buffers.put(EvidenceRole.DETAIL, 0, SecureImageBuffer.wrap(all[1]))
        buffers.put(EvidenceRole.DETAIL, 1, SecureImageBuffer.wrap(all[2]))

        buffers.clear()
        buffers.clear()

        assertTrue(all.all { array -> array.all { it == 0.toByte() } })
        assertEquals(0, buffers.size)
    }

    @Test
    fun `las fotos de un rol salen ordenadas por indice`() {
        val buffers = EvidenceBuffers()
        buffers.put(EvidenceRole.DETAIL, 2, SecureImageBuffer.wrap(photo(3)))
        buffers.put(EvidenceRole.DETAIL, 0, SecureImageBuffer.wrap(photo(1)))
        buffers.put(EvidenceRole.GENERAL, 0, SecureImageBuffer.wrap(photo(9)))
        buffers.put(EvidenceRole.DETAIL, 1, SecureImageBuffer.wrap(photo(2)))

        val detail = buffers.photos(EvidenceRole.DETAIL).map { it.read { bytes -> bytes[0] } }

        assertEquals(listOf<Byte>(1, 2, 3), detail)
    }
}
