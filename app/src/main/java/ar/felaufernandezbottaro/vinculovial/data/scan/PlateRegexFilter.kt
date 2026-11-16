package ar.felaufernandezbottaro.vinculovial.data.scan

import android.util.Log
import ar.felaufernandezbottaro.vinculovial.BuildConfig
import ar.felaufernandezbottaro.vinculovial.domain.scan.model.RecognizedText
import javax.inject.Inject

/** Patente elegida entre los candidatos del OCR y la línea cruda de la que salió. */
data class PlateMatch(val plate: String, val source: RecognizedText)

/**
 * Valida texto contra el formato de patente argentina (viejo `AAA000`, nuevo Mercosur
 * `AA000AA`) y elige la patente a partir de lo que leyó el OCR. Tiene dos caminos con criterios
 * distintos a propósito:
 *
 * - [select], para el OCR de cámara: entre todos los candidatos que, normalizados por posición
 *   con [PlateNormalizer], son una patente válida, elige el de mayor confianza. El texto sale
 *   siempre de uno de los candidatos que devolvió el OCR, corregido carácter a carácter; no se
 *   inventa ni se combina texto. La corrección de confusiones típicas (O/0, I/1...) puede
 *   producir una patente que no es la del vehículo, por eso este camino termina siempre en la
 *   confirmación humana de `ScanViewModel.onPlateConfirmed`.
 * - [filter], para la patente que el usuario tipea a mano: sólo valida el formato, sin corregir.
 *   Ahí tipear ES la confirmación y el chequeo arranca sin otro paso, así que no se le cambia en
 *   silencio lo que escribió.
 *
 * En los dos se normaliza a mayúsculas sin espacios ni guiones.
 */
class PlateRegexFilter @Inject constructor() {

    /**
     * Candidato de mayor confianza que, normalizado, es una patente válida; `null` si ninguno
     * lo es. A igual confianza gana el que viene primero.
     */
    fun select(candidates: List<RecognizedText>): String? = selectMatch(candidates)?.plate

    /** Igual que [select], pero conserva la línea cruda de la que salió la patente. */
    fun selectMatch(candidates: List<RecognizedText>): PlateMatch? {
        logRaw(candidates.map { it.text })

        return candidates
            .mapNotNull { candidate -> PlateNormalizer.normalize(candidate.text)?.let { PlateMatch(it, candidate) } }
            .maxByOrNull { it.source.confidence }
    }

    /**
     * Devuelve la primera patente con el formato exacto entre los candidatos, sin corrección de
     * caracteres, o `null` si ninguna matchea.
     */
    fun filter(candidates: List<String>): String? {
        logRaw(candidates)

        candidates.forEach { candidate ->
            normalizeStrict(candidate)?.let { return it }
        }
        return null
    }

    private fun logRaw(candidates: List<String>) {
        // Sólo en debug: sin esto depurar el OCR es a ciegas, pero el texto crudo (incluida
        // una patente real) no puede llegar jamás a un log de producción -- mismo criterio
        // que el diagnóstico de resolución en CameraInternals.captureJpegInternal.
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "candidatos OCR crudos: $candidates")
        }
    }

    private fun normalizeStrict(raw: String): String? {
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
