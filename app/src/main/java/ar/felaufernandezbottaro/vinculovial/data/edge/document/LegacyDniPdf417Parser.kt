package ar.felaufernandezbottaro.vinculovial.data.edge.document

import javax.inject.Inject

/**
 * Parser del PDF417 del DNI actual (tarjeta con código de barras en el mismo frente).
 *
 * El contenido es texto plano con campos separados por `@`. Hay dos disposiciones en
 * circulación según el año de emisión:
 *
 * ```
 * A (2012-2014): tramite@apellido@nombres@sexo@dni@ejemplar@nacimiento@emision
 * B (2015+)    : dni@ejemplar@apellido@nombres@sexo@documento@tramite@nacimiento@...
 * ```
 *
 * En vez de asumir una, se identifica la disposición por la forma de los campos: el
 * sexo (`M`/`F`) es el ancla más confiable y el número de documento es el campo
 * numérico de 7-8 dígitos adyacente.
 */
class LegacyDniPdf417Parser @Inject constructor() : DocumentDataParser {

    override fun parse(rawData: String): ArgentineDni? {
        val fields = rawData.trim().split(SEPARATOR).map(String::trim)
        if (fields.size < MIN_FIELDS) return null

        val sexIndex = fields.indexOfFirst { SEX.matches(it) }
        if (sexIndex < 2) return null

        // Apellido y nombres son los dos campos inmediatamente anteriores al sexo.
        val surname = fields[sexIndex - 2]
        val givenNames = fields[sexIndex - 1]
        if (!NAME.matches(surname) || !NAME.matches(givenNames)) return null

        // El número está justo después del sexo en la disposición A; en la B ya apareció
        // como primer campo. Se busca en ese orden para no confundirlo con el trámite.
        val number = fields.getOrNull(sexIndex + 1)?.takeIf { DNI_NUMBER.matches(it) }
            ?: fields.first().takeIf { DNI_NUMBER.matches(it) }
            ?: return null

        // El número se devuelve tal cual viene: normalizar ceros a la izquierda es una
        // regla de dominio (CredentialRules.normalizeDni), no del formato del código.
        return ArgentineDni(
            number = number,
            surname = surname.uppercase(),
            givenNames = givenNames.uppercase(),
            sex = fields[sexIndex].uppercase(),
            procedureNumber = fields.firstOrNull { PROCEDURE_NUMBER.matches(it) },
        )
    }

    private companion object {
        const val SEPARATOR = '@'
        const val MIN_FIELDS = 6

        val DNI_NUMBER = Regex("^\\d{7,8}$")

        /**
         * El trámite se identifica por longitud, no por posición: va primero en la
         * disposición A y después del sexo en la B, pero en las dos es netamente más largo
         * que un número de documento (7-8 dígitos), así que el rango 9-11 lo separa sin
         * ambigüedad y sin depender de la disposición.
         */
        val PROCEDURE_NUMBER = Regex("^\\d{9,11}$")

        val SEX = Regex("^[MF]$", RegexOption.IGNORE_CASE)
        val NAME = Regex("^[A-ZÁÉÍÓÚÜÑ][A-ZÁÉÍÓÚÜÑ '.-]*$", RegexOption.IGNORE_CASE)
    }
}
