package com.vinculovial.app.data.edge.document

import org.json.JSONException
import org.json.JSONObject

/**
 * Parser del QR del reverso del DNI electrónico (tarjeta con chip, emisión desde 2026).
 *
 * ⚠️ **Formato sin confirmar contra un documento real.** Las fuentes públicas disponibles
 * coinciden en que el QR trae nombre, número de documento, fecha de nacimiento y número
 * de trámite, pero ninguna especifica la serialización exacta a nivel de bytes. Se
 * prueban, en orden, las formas más probables:
 * 1. el mismo delimitado por `@` del PDF417 actual — plausible por reutilización de un
 *    formato ya existente en RENAPER;
 * 2. JSON, con el número bajo alguna de las claves habituales;
 * 3. una URL de verificación con el número como query param.
 *
 * Si ninguna aplica, se devuelve `null`: **nunca se inventa un número**. Antes de confiar
 * en esto hay que validarlo contra una tarjeta real y, si el formato es otro (por ejemplo
 * un token opaco que sólo tiene sentido para un servicio de verificación de RENAPER, en
 * cuyo caso la extracción ya no podría ser 100% on-device), reemplazar esta clase.
 */
internal object ArgentineDniQrParser {

    private val DNI_NUMBER = Regex("^\\d{7,8}$")

    private val JSON_KEYS = listOf(
        "dni", "nrodni", "nro_dni", "documento", "nrodocumento", "nro_documento",
        "numerodocumento", "numero_documento", "numdoc", "num_doc",
    )

    private val QUERY_PARAM = Regex("""[?&](?:dni|documento)=(\d{7,8})""", RegexOption.IGNORE_CASE)

    fun parseDniNumber(rawQr: String): String? =
        fromDelimited(rawQr) ?: fromJson(rawQr) ?: fromQueryParam(rawQr)

    private fun fromDelimited(raw: String): String? = ArgentineDniParser.parse(raw)?.number

    private fun fromJson(raw: String): String? = try {
        val json = JSONObject(raw)
        json.keys().asSequence()
            .filter { key -> JSON_KEYS.any { candidate -> candidate.equals(key, ignoreCase = true) } }
            .map { key -> json.optString(key) }
            .firstOrNull { it.matches(DNI_NUMBER) }
    } catch (e: JSONException) {
        null
    }

    private fun fromQueryParam(raw: String): String? =
        QUERY_PARAM.find(raw)?.groupValues?.get(1)?.takeIf { it.matches(DNI_NUMBER) }
}
