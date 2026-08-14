package com.vinculovial.app.data.edge.document

/**
 * Datos del titular tal como vienen en el PDF417. Sólo el número sale de esta capa;
 * el resto se usa para validar que el código realmente sea un DNI.
 */
internal data class ArgentineDni(
    val number: String,
    val surname: String,
    val givenNames: String,
    val sex: String,
) {
    /** Nunca renderizar los datos del titular. */
    override fun toString(): String = "ArgentineDni(***)"
}

/**
 * Parser del PDF417 del DNI argentino.
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
 *
 * Está aislado y cubierto por tests porque es el punto más frágil del pipeline: si
 * aparece una disposición nueva, se arregla acá sin tocar nada más.
 */
internal object ArgentineDniParser {

    private const val SEPARATOR = '@'
    private const val MIN_FIELDS = 6

    private val DNI_NUMBER = Regex("^\\d{7,8}$")
    private val SEX = Regex("^[MF]$", RegexOption.IGNORE_CASE)
    private val NAME = Regex("^[A-ZÁÉÍÓÚÜÑ][A-ZÁÉÍÓÚÜÑ '.-]*$", RegexOption.IGNORE_CASE)

    /** `null` si el contenido no corresponde a un DNI argentino. */
    fun parse(rawBarcode: String): ArgentineDni? {
        val fields = rawBarcode.trim().split(SEPARATOR).map(String::trim)
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
        )
    }
}
