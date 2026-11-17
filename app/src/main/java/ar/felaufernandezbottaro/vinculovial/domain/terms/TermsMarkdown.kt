package ar.felaufernandezbottaro.vinculovial.domain.terms

/**
 * Los datos del responsable de la base y la versión de los Términos (US-19). Vienen de
 * `BuildConfig` (`LEGAL_*` y `TERMS_VERSION`, configurables por `local.properties`), así que el
 * texto de `res/raw/terminos.md` no se toca para completarlos.
 */
data class LegalInfo(
    val responsibleName: String,
    val contactEmail: String,
    val address: String,
    /** La versión de los Términos que se acepta en el alta; viaja en el payload del signup. */
    val version: String,
) {
    /** Marcador del documento -> valor. */
    fun placeholders(): Map<String, String> = mapOf(
        "RESPONSABLE" to responsibleName,
        "EMAIL_CONTACTO" to contactEmail,
        "DOMICILIO" to address,
        "VERSION" to version,
    )
}

/** Un tramo de texto de una línea del documento. */
data class TermsSpan(val text: String, val bold: Boolean = false)

/** Los bloques del documento que el renderizador sabe dibujar. */
sealed interface TermsBlock {

    /** `# Título` (nivel 1) y `## Sección` (nivel 2). */
    data class Heading(val level: Int, val text: String) : TermsBlock

    data class Paragraph(val spans: List<TermsSpan>) : TermsBlock

    /** Una lista de `- ` ítems consecutivos. */
    data class Bullets(val items: List<List<TermsSpan>>) : TermsBlock

    /**
     * Una tabla de markdown. [rows] son las filas de datos (sin la de títulos ni la de
     * separadores); cada celda corresponde, en orden, a un elemento de [headers].
     */
    data class Table(val headers: List<String>, val rows: List<List<List<TermsSpan>>>) : TermsBlock
}

/**
 * Renderizador mínimo de markdown (US-19): títulos, párrafos, listas, negritas y tablas, que es
 * lo único que usa `terminos.md`. No es un parser de CommonMark: lo que no reconoce (cursiva,
 * enlaces, código) se muestra como texto literal, nunca se pierde.
 *
 * Los marcadores `{{NOMBRE}}` se reemplazan en cada tramo ya parseado, no antes de parsear: así un
 * valor configurado con `**` o `|` no puede alterar la estructura del documento. Un marcador que
 * no está en [placeholders] queda tal cual.
 */
object TermsMarkdown {

    fun parse(markdown: String, placeholders: Map<String, String> = emptyMap()): List<TermsBlock> {
        val blocks = mutableListOf<TermsBlock>()
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')

        var paragraph = mutableListOf<String>()
        var bullets = mutableListOf<List<TermsSpan>>()
        var table = mutableListOf<String>()

        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += TermsBlock.Paragraph(inline(paragraph.joinToString(" "), placeholders))
                paragraph = mutableListOf()
            }
            if (bullets.isNotEmpty()) {
                blocks += TermsBlock.Bullets(bullets)
                bullets = mutableListOf()
            }
            if (table.isNotEmpty()) {
                blocks += tableOf(table, placeholders)
                table = mutableListOf()
            }
        }

        for (raw in lines) {
            val line = raw.trim()
            when {
                line.isEmpty() -> flush()

                line.startsWith("#") -> {
                    flush()
                    val level = line.takeWhile { it == '#' }.length
                    blocks += TermsBlock.Heading(level, fill(line.drop(level).trim(), placeholders))
                }

                line.startsWith("- ") -> {
                    if (paragraph.isNotEmpty() || table.isNotEmpty()) flush()
                    bullets += inline(line.drop(2).trim(), placeholders)
                }

                line.startsWith("|") -> {
                    if (paragraph.isNotEmpty() || bullets.isNotEmpty()) flush()
                    table += line
                }

                else -> {
                    if (bullets.isNotEmpty() || table.isNotEmpty()) flush()
                    paragraph += line
                }
            }
        }
        flush()
        return blocks
    }

    private fun tableOf(lines: List<String>, placeholders: Map<String, String>): TermsBlock {
        val cells = lines.map { row -> row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() } }
        val headers = cells.first().map { fill(it, placeholders) }
        val rows = cells.drop(1)
            .filterNot { row -> row.all { cell -> cell.isNotEmpty() && cell.all { it == '-' || it == ':' } } }
            .map { row -> row.map { inline(it, placeholders) } }
        return TermsBlock.Table(headers, rows)
    }

    /** `**negrita**`. Un `**` sin cierre queda como texto literal. */
    private fun inline(text: String, placeholders: Map<String, String>): List<TermsSpan> {
        val spans = mutableListOf<TermsSpan>()
        var rest = text
        while (rest.isNotEmpty()) {
            val open = rest.indexOf("**")
            val close = if (open >= 0) rest.indexOf("**", open + 2) else -1
            if (open < 0 || close < 0) {
                spans += TermsSpan(fill(rest, placeholders))
                break
            }
            if (open > 0) spans += TermsSpan(fill(rest.substring(0, open), placeholders))
            spans += TermsSpan(fill(rest.substring(open + 2, close), placeholders), bold = true)
            rest = rest.substring(close + 2)
        }
        return spans
    }

    // Las dos llaves de cierre van escapadas: la JVM acepta un `}` suelto, pero el motor de regex de
    // Android (ICU) lo rechaza al inicializar el objeto, y eso tumba la pantalla de Términos.
    private val PLACEHOLDER = Regex("\\{\\{([A-Z_]+)\\}\\}")

    private fun fill(text: String, placeholders: Map<String, String>): String =
        PLACEHOLDER.replace(text) { match -> placeholders[match.groupValues[1]] ?: match.value }
}
