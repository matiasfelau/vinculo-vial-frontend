package ar.felaufernandezbottaro.vinculovial.data.scan

/**
 * Normalización de texto de OCR a patente argentina, por posición: según el formato (viejo
 * `ABC123`, Mercosur `AB123CD`) cada posición es de letra o de número, y un carácter que el OCR
 * confundió con su parecido del otro tipo se convierte.
 *
 * - posición de letra: `0→O 1→I 8→B 5→S 2→Z 6→G`
 * - posición de número: la inversa (`O→0 I→1 B→8 S→5 Z→2 G→6`)
 *
 * El formato lo fija el largo del texto ya limpio (6 → viejo, 7 → Mercosur). Un carácter que no
 * es del tipo de su posición ni tiene conversión, o un largo que no es ninguno de los dos, hace
 * que el candidato se descarte: nunca se inventan caracteres ni se prueban alternativas.
 *
 * Replica la normalización por posición del servidor; si la regla cambia allá, tiene que cambiar
 * acá.
 */
object PlateNormalizer {

    private val NOISE = Regex("[\\s-]")

    private val DIGIT_TO_LETTER = mapOf('0' to 'O', '1' to 'I', '8' to 'B', '5' to 'S', '2' to 'Z', '6' to 'G')
    private val LETTER_TO_DIGIT = DIGIT_TO_LETTER.entries.associate { (digit, letter) -> letter to digit }

    private const val LETTER = 'L'
    private const val NUMBER = 'N'
    private const val VIEJA = "LLLNNN"
    private const val MERCOSUR = "LLNNNLL"

    /** Patente normalizada, o `null` si el texto no puede ser una patente de ningún formato. */
    fun normalize(raw: String): String? {
        val cleaned = raw.trim().uppercase().replace(NOISE, "")
        val pattern = when (cleaned.length) {
            VIEJA.length -> VIEJA
            MERCOSUR.length -> MERCOSUR
            else -> return null
        }
        val out = StringBuilder(cleaned.length)
        for ((i, c) in cleaned.withIndex()) {
            val fixed = if (pattern[i] == LETTER) toLetter(c) else toDigit(c)
            out.append(fixed ?: return null)
        }
        return out.toString()
    }

    private fun toLetter(c: Char): Char? = when (c) {
        in 'A'..'Z' -> c
        else -> DIGIT_TO_LETTER[c]
    }

    private fun toDigit(c: Char): Char? = when (c) {
        in '0'..'9' -> c
        else -> LETTER_TO_DIGIT[c]
    }
}
