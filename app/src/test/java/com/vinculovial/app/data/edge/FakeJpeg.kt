package com.vinculovial.app.data.edge

/**
 * Construye JPEG mínimos pero estructuralmente válidos, para ejercitar el parser de
 * cabecera y las puertas de calidad sin depender de fotos reales en el repo.
 *
 * Estructura: `SOI · COM(relleno) · SOF0(dimensiones) · EOI`. El segmento de comentario
 * va primero a propósito: obliga al parser a saltarlo por longitud antes de dar con el
 * SOF, que es justo la parte que puede romperse.
 */
internal object FakeJpeg {

    private const val MAX_SEGMENT_PAYLOAD = 65_533

    fun of(width: Int, height: Int, sizeBytes: Int = 40_000): ByteArray {
        val out = ArrayList<Byte>(sizeBytes + 64)

        out.addMarker(0xD8) // SOI

        var remaining = (sizeBytes - MINIMUM_OVERHEAD).coerceAtLeast(0)
        while (remaining > 0) {
            val payload = minOf(remaining, MAX_SEGMENT_PAYLOAD)
            out.addMarker(0xFE) // COM
            out.addShort(payload + 2)
            repeat(payload) { out.add(0x20) }
            remaining -= payload
        }

        out.addMarker(0xC0) // SOF0
        out.addShort(17)
        out.add(8) // precisión
        out.addShort(height)
        out.addShort(width)
        out.add(3) // componentes
        listOf(0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01).forEach { out.add(it.toByte()) }

        out.addMarker(0xD9) // EOI

        return out.toByteArray()
    }

    /** Una captura de documento que pasa todas las puertas de calidad. */
    fun usableDocument(): ByteArray = of(width = 1920, height = 1080)

    /** Una selfie que pasa todas las puertas de calidad. */
    fun usableSelfie(): ByteArray = of(width = 960, height = 1280)

    private const val MINIMUM_OVERHEAD = 2 + 19 + 2

    private fun MutableList<Byte>.addMarker(marker: Int) {
        add(0xFF.toByte())
        add(marker.toByte())
    }

    private fun MutableList<Byte>.addShort(value: Int) {
        add(((value shr 8) and 0xFF).toByte())
        add((value and 0xFF).toByte())
    }

    private fun MutableList<Byte>.add(value: Int) = add(value.toByte())
}
