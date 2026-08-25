package ar.felaufernandezbottaro.vinculovial.data.scan

import ar.felaufernandezbottaro.vinculovial.domain.scan.model.OverlayRect
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `cropRect` es pura aritmética de enteros (sin `android.graphics.Rect`), así que se puede
 * probar sin Robolectric. Los valores esperados de las rotaciones se calcularon a mano
 * aplicando la inversa de "rotar el buffer `rotationDegrees` en sentido horario para dejarlo
 * vertical" -- ver el KDoc de `mapUprightToSensor` en `FrameCropper.kt`.
 */
class FrameCropperTest {

    @Test
    fun `rotacion 0 mapea directo, sin swap de dimensiones`() {
        // OverlayRect.PLATE real, sobre una resolución de análisis típica en vertical.
        val rect = cropRect(
            sensorWidth = 960,
            sensorHeight = 1280,
            rotationDegrees = 0,
            overlay = OverlayRect.PLATE,
        )

        assertEquals(PixelRect(left = 96, top = 499, right = 864, bottom = 781), rect)
    }

    @Test
    fun `rotacion 90 intercambia ancho y alto, y la mitad izquierda del frame vertical cae abajo en el sensor`() {
        val overlay = OverlayRect(leftFraction = 0f, topFraction = 0f, widthFraction = 0.5f, heightFraction = 1f)

        val rect = cropRect(sensorWidth = 100, sensorHeight = 200, rotationDegrees = 90, overlay = overlay)

        assertEquals(PixelRect(left = 0, top = 99, right = 100, bottom = 199), rect)
    }

    @Test
    fun `rotacion 180 invierte ambos ejes`() {
        val overlay = OverlayRect(leftFraction = 0f, topFraction = 0f, widthFraction = 0.5f, heightFraction = 1f)

        val rect = cropRect(sensorWidth = 100, sensorHeight = 200, rotationDegrees = 180, overlay = overlay)

        assertEquals(PixelRect(left = 49, top = 0, right = 99, bottom = 199), rect)
    }

    @Test
    fun `rotacion 270 es la especular de 90`() {
        val overlay = OverlayRect(leftFraction = 0f, topFraction = 0f, widthFraction = 0.5f, heightFraction = 1f)

        val rect = cropRect(sensorWidth = 100, sensorHeight = 200, rotationDegrees = 270, overlay = overlay)

        assertEquals(PixelRect(left = 0, top = 0, right = 99, bottom = 100), rect)
    }

    @Test
    fun `una rotacion negativa se normaliza al equivalente positivo`() {
        val overlay = OverlayRect(leftFraction = 0f, topFraction = 0f, widthFraction = 0.5f, heightFraction = 1f)

        val rect = cropRect(sensorWidth = 100, sensorHeight = 200, rotationDegrees = -90, overlay = overlay)

        assertEquals(cropRect(100, 200, 270, overlay), rect)
    }

    @Test
    fun `una rotacion mayor a 360 se normaliza al equivalente dentro del rango`() {
        val overlay = OverlayRect(leftFraction = 0f, topFraction = 0f, widthFraction = 0.5f, heightFraction = 1f)

        val rect = cropRect(sensorWidth = 100, sensorHeight = 200, rotationDegrees = 450, overlay = overlay)

        assertEquals(cropRect(100, 200, 90, overlay), rect)
    }

    @Test
    fun `el mismo overlay escala proporcional al tamano del frame`() {
        val overlay = OverlayRect(leftFraction = 0.25f, topFraction = 0.25f, widthFraction = 0.5f, heightFraction = 0.5f)

        val small = cropRect(sensorWidth = 200, sensorHeight = 200, rotationDegrees = 0, overlay = overlay)
        val large = cropRect(sensorWidth = 400, sensorHeight = 400, rotationDegrees = 0, overlay = overlay)

        assertEquals(PixelRect(50, 50, 150, 150), small)
        assertEquals(PixelRect(100, 100, 300, 300), large)
    }

    @Test
    fun `un overlay que por redondeo se sale del frame queda acotado a sus limites`() {
        val overlay = OverlayRect(leftFraction = 0.99f, topFraction = 0f, widthFraction = 0.05f, heightFraction = 0.05f)

        val rect = cropRect(sensorWidth = 100, sensorHeight = 100, rotationDegrees = 0, overlay = overlay)

        assertEquals(PixelRect(left = 99, top = 0, right = 100, bottom = 5), rect)
    }
}
