package ar.felaufernandezbottaro.vinculovial.data.edge

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cuánto submuestrea la decodificación es lo que decide si el PDF417 sobrevive.
 *
 * `inSampleSize` sólo acepta potencias de dos, así que "quedar por debajo del objetivo" y
 * "quedar por encima" pueden diferir en un factor 2 — invisible para el reconocimiento facial
 * (que reduce a 112x112 igual) y fatal para un código de barras, que necesita un mínimo de
 * píxeles por módulo y no puede recuperarlos después. Estos casos fijan esa decisión.
 */
class SecureBitmapSampleSizeTest {

    @Test
    fun `nunca deja el lado mayor por debajo del objetivo`() {
        // Una captura de 12MP contra el objetivo de 1600: dividir por 4 daría 1000px, un 37%
        // por debajo de lo pedido. Es exactamente la pérdida que volvía ilegible el código.
        assertEquals(2, SecureBitmap.sampleSizeFor(width = 3_000, height = 4_000, targetDimension = 1_600))
    }

    @Test
    fun `submuestrea cuando puede hacerlo sin cruzar el objetivo`() {
        assertEquals(4, SecureBitmap.sampleSizeFor(width = 3_000, height = 4_000, targetDimension = 1_000))
        assertEquals(8, SecureBitmap.sampleSizeFor(width = 3_000, height = 4_000, targetDimension = 500))
    }

    @Test
    fun `no toca una imagen que ya es mas chica que el objetivo`() {
        assertEquals(1, SecureBitmap.sampleSizeFor(width = 800, height = 600, targetDimension = 1_600))
    }

    @Test
    fun `el objetivo exacto no dispara un escalon de mas`() {
        // 2000 / 2 = 1000, que es justo el objetivo: entra, y bajar otro escalón no.
        assertEquals(2, SecureBitmap.sampleSizeFor(width = 1_500, height = 2_000, targetDimension = 1_000))
    }

    @Test
    fun `tolera dimensiones degeneradas sin dividir por cero`() {
        assertEquals(1, SecureBitmap.sampleSizeFor(width = 0, height = 0, targetDimension = 1_600))
    }
}
