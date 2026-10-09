package ar.felaufernandezbottaro.vinculovial.data.evidence

import ar.felaufernandezbottaro.vinculovial.data.edge.SecureImageBuffer
import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidenceRole

/**
 * Las fotos de evidencia ya procesadas (JPEG en claro, sin EXIF), una por casillero
 * `(rol, índice)`. Viven sólo en memoria y se liberan con [clear]: en el envío exitoso, en la
 * cancelación y en `onCleared` del `ViewModel`. Reemplazar o quitar una foto libera la anterior.
 *
 * No es thread-safe: lo usa el `ViewModel` desde el hilo principal.
 */
class EvidenceBuffers {

    private data class Slot(val role: EvidenceRole, val index: Int)

    private val slots = linkedMapOf<Slot, SecureImageBuffer>()

    /** Guarda [photo] y libera la que ocupaba el casillero. Toma posesión de [photo]. */
    fun put(role: EvidenceRole, index: Int, photo: SecureImageBuffer) {
        slots.put(Slot(role, index), photo)?.clear()
    }

    fun remove(role: EvidenceRole, index: Int) {
        slots.remove(Slot(role, index))?.clear()
    }

    fun has(role: EvidenceRole, index: Int): Boolean = Slot(role, index) in slots

    /** Presta los bytes de la foto al [block]; `null` si el casillero está vacío. */
    fun <R> read(role: EvidenceRole, index: Int, block: (ByteArray) -> R): R? =
        slots[Slot(role, index)]?.read(block)

    /** Las fotos de [role], ordenadas por índice. El llamador no debe liberarlas ni guardarlas. */
    fun photos(role: EvidenceRole): List<SecureImageBuffer> =
        slots.entries.filter { it.key.role == role }.sortedBy { it.key.index }.map { it.value }

    val size: Int get() = slots.size

    /** Sobreescribe con ceros todas las fotos y vacía el conjunto. Idempotente. */
    fun clear() {
        slots.values.forEach { it.clear() }
        slots.clear()
    }
}
