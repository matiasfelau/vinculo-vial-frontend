package ar.felaufernandezbottaro.vinculovial.data.scan

import android.util.Log
import ar.felaufernandezbottaro.vinculovial.BuildConfig
import javax.inject.Inject

/**
 * Valida y normaliza texto candidato de OCR contra el formato de patente argentina (viejo
 * `AAA000`, nuevo Mercosur `AA000AA`), normalizando a mayúsculas sin espacios ni guiones.
 *
 * Sin corrección de ambigüedades típicas de OCR (O/0, I/1): un candidato con un carácter fuera
 * de lugar para su posición se descarta en vez de adivinar. Corregir mal acá significa
 * consultar -- y potencialmente alertar sobre -- la patente de otra persona, así que ante la
 * duda gana el falso negativo (se sigue escaneando) sobre el falso positivo.
 */
class PlateRegexFilter @Inject constructor() {

    /** Devuelve la primera patente válida entre los candidatos, o `null` si ninguna matchea. */
    fun filter(candidates: List<String>): String? {
        // Sólo en debug: sin esto depurar el OCR es a ciegas, pero el texto crudo (incluida
        // una patente real) no puede llegar jamás a un log de producción -- mismo criterio
        // que el diagnóstico de resolución en CameraInternals.captureJpegInternal.
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "candidatos OCR crudos: $candidates")
        }

        candidates.forEach { candidate ->
            normalize(candidate)?.let { return it }
        }
        return null
    }

    private fun normalize(raw: String): String? {
        val cleaned = raw.trim().uppercase().replace(NOISE, "")
        return if (VIEJA.matches(cleaned) || MERCOSUR.matches(cleaned)) cleaned else null
    }

    private companion object {
        const val TAG = "PlateRegexFilter"
        val NOISE = Regex("[\\s-]")
        val VIEJA = Regex("^[A-Z]{3}\\d{3}$")
        val MERCOSUR = Regex("^[A-Z]{2}\\d{3}[A-Z]{2}$")
    }
}
