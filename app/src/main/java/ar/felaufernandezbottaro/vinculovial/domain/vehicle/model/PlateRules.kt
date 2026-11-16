package ar.felaufernandezbottaro.vinculovial.domain.vehicle.model

/**
 * Reglas del dominio (patente) que el usuario escribe a mano para vincular o desvincular un
 * vehículo (US-14). Vehicle vuelve a validar: esto es UX y evita pedidos que el servidor va a
 * rechazar, no seguridad.
 *
 * Formatos admitidos: `AAA999` (anterior a 2016) y `AA999AA` (Mercosur). Sin corrección de
 * ambigüedades (O/0, I/1): una patente mal escrita se rechaza en vez de adivinarse, porque
 * corregirla mal vincularía el vehículo de otra persona.
 */
object PlateRules {

    const val MAX_LENGTH = 7

    private val OLD_FORMAT = Regex("^[A-Z]{3}\\d{3}$")
    private val MERCOSUR_FORMAT = Regex("^[A-Z]{2}\\d{3}[A-Z]{2}$")

    /**
     * Lo que queda en el campo mientras se escribe: mayúsculas, sin espacios ni guiones ni
     * ningún otro símbolo, y como mucho [MAX_LENGTH] caracteres. Sólo A-Z y 0-9.
     */
    fun normalize(raw: String): String = clean(raw).take(MAX_LENGTH)

    /**
     * Igual que [normalize] pero sin cortar: lo que se valida antes de mandar. Una patente de más de
     * 7 caracteres no se acepta por quedarse con su prefijo, porque sería vincular otra patente.
     */
    fun clean(raw: String): String = raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    /** `true` si [plate] (ya normalizada) es `AAA999` o `AA999AA`. */
    fun isValid(plate: String): Boolean = OLD_FORMAT.matches(plate) || MERCOSUR_FORMAT.matches(plate)
}
