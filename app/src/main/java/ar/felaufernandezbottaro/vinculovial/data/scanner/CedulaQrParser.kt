package ar.felaufernandezbottaro.vinculovial.data.scanner

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaData
import javax.inject.Inject

/**
 * Parser del contenido del QR de la cédula del vehículo.
 *
 * Formato de texto plano `dni@patente`, misma convención de separador que el PDF417 del
 * DNI (ver `LegacyDniPdf417Parser`). Es el único punto que hay que tocar si el backend
 * termina publicando un contenido distinto para el QR real.
 */
class CedulaQrParser @Inject constructor() {

    fun parse(rawData: String): CedulaData? {
        val fields = rawData.trim().split(SEPARATOR).map(String::trim)
        if (fields.size != FIELD_COUNT) return null

        val (dni, patente) = fields
        if (!DNI.matches(dni)) return null
        if (!PATENTE_VIEJA.matches(patente) && !PATENTE_MERCOSUR.matches(patente)) return null

        return CedulaData(dni = dni, patente = patente.uppercase())
    }

    private companion object {
        const val SEPARATOR = '@'
        const val FIELD_COUNT = 2

        val DNI = Regex("^\\d{7,8}$")

        /** Formato histórico: 3 letras + 3 dígitos (p. ej. ABC123). */
        val PATENTE_VIEJA = Regex("^[A-Z]{3}\\d{3}$", RegexOption.IGNORE_CASE)

        /** Formato Mercosur, vigente desde 2016: 2 letras + 3 dígitos + 2 letras (p. ej. AB123CD). */
        val PATENTE_MERCOSUR = Regex("^[A-Z]{2}\\d{3}[A-Z]{2}$", RegexOption.IGNORE_CASE)
    }
}
