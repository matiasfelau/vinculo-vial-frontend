package ar.felaufernandezbottaro.vinculovial.domain.scan

import android.graphics.Bitmap

/**
 * Reconocimiento de texto sobre un frame ya recortado al overlay. Única implementación de
 * este CU: `MLKitRecognitionStrategy`, que corre en el dispositivo -- el objetivo declarado
 * es poder sustituirla a futuro por una estrategia remota sin tocar el caso de uso ni la UI.
 *
 * La firma recibe un bitmap ya recortado y devuelve texto candidato; no expone tipos de ML
 * Kit ni de CameraX. Si la firma filtrara el SDK, la abstracción no serviría para lo que fue
 * creada.
 */
interface PlateRecognitionStrategy {

    /** Cada elemento es una línea de texto candidata; ninguna está garantizada como patente. */
    suspend fun recognize(bitmap: Bitmap): List<String>
}
