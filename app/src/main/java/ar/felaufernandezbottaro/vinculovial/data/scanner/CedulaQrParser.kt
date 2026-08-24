package ar.felaufernandezbottaro.vinculovial.data.scanner

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaData
import javax.inject.Inject

/**
 * Parser del contenido del QR de la cédula del vehículo (DNRPA).
 *
 * Confirmado contra una cédula física real: tres líneas de texto plano separadas por
 * salto de línea (`\n` y/o `\r\n`), sin delimitador ni etiqueta de campo. El orden
 * observado es chasis, patente, número de control -- no hay una especificación oficial
 * publicada de la DNRPA para este contenido; si aparece, éste es el único punto que hay
 * que ajustar.
 *
 * Ejemplo real:
 * ```
 * APJ56565
 * GUQ856
 * 5092160
 * ```
 */
class CedulaQrParser @Inject constructor() {

    fun parse(rawData: String): CedulaData? {
        val lines = rawData.lines().map(String::trim).filter(String::isNotBlank)
        if (lines.size != LINE_COUNT) return null

        val (chasis, patente, control) = lines
        if (!CHASIS.matches(chasis)) return null
        if (!PATENTE_VIEJA.matches(patente) && !PATENTE_MERCOSUR.matches(patente)) return null
        if (!NRO_CONTROL.matches(control)) return null

        return CedulaData(
            chasis = chasis.uppercase(),
            patente = patente.uppercase(),
            nroControl = control,
        )
    }

    private companion object {
        const val LINE_COUNT = 3

        /** Longitud observada 5-17: cubre tanto chasis abreviados como un VIN completo (17). */
        val CHASIS = Regex("^[A-Z0-9]{5,17}$", RegexOption.IGNORE_CASE)

        /** Formato histórico: 3 letras + 3 dígitos (p. ej. ABC123). */
        val PATENTE_VIEJA = Regex("^[A-Z]{3}\\d{3}$", RegexOption.IGNORE_CASE)

        /** Formato Mercosur, vigente desde 2016: 2 letras + 3 dígitos + 2 letras (p. ej. AB123CD). */
        val PATENTE_MERCOSUR = Regex("^[A-Z]{2}\\d{3}[A-Z]{2}$", RegexOption.IGNORE_CASE)

        val NRO_CONTROL = Regex("^\\d{5,10}$")
    }
}
