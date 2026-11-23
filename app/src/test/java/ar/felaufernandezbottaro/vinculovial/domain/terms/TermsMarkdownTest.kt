package ar.felaufernandezbottaro.vinculovial.domain.terms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El renderizador mínimo de markdown de US-19: títulos, párrafos, listas, negritas, tablas y marcadores. */
class TermsMarkdownTest {

    private fun parse(md: String, placeholders: Map<String, String> = emptyMap()) =
        TermsMarkdown.parse(md, placeholders)

    private fun text(vararg spans: TermsSpan) = spans.toList()

    // -- Títulos y párrafos -------------------------------------------------------------------

    @Test
    fun `un titulo de nivel 1 y uno de nivel 2`() {
        val blocks = parse("# Terminos\n\n## 1. Quienes somos")

        assertEquals(
            listOf(TermsBlock.Heading(1, "Terminos"), TermsBlock.Heading(2, "1. Quienes somos")),
            blocks,
        )
    }

    @Test
    fun `un parrafo`() {
        assertEquals(
            listOf(TermsBlock.Paragraph(text(TermsSpan("Hola mundo.")))),
            parse("Hola mundo."),
        )
    }

    @Test
    fun `las lineas seguidas son un solo parrafo y una linea en blanco los separa`() {
        val blocks = parse("primera linea\nsegunda linea\n\notro parrafo")

        assertEquals(
            listOf(
                TermsBlock.Paragraph(text(TermsSpan("primera linea segunda linea"))),
                TermsBlock.Paragraph(text(TermsSpan("otro parrafo"))),
            ),
            blocks,
        )
    }

    @Test
    fun `los saltos de linea de Windows no cambian nada`() {
        assertEquals(parse("# A\n\nTexto\n\n- uno\n- dos"), parse("# A\r\n\r\nTexto\r\n\r\n- uno\r\n- dos"))
    }

    @Test
    fun `un documento vacio no tiene bloques`() {
        assertEquals(emptyList<TermsBlock>(), parse(""))
        assertEquals(emptyList<TermsBlock>(), parse("\n\n   \n"))
    }

    // -- Listas -------------------------------------------------------------------------------

    @Test
    fun `una lista de viñetas`() {
        val blocks = parse("- uno\n- dos\n- tres")

        assertEquals(
            listOf(
                TermsBlock.Bullets(
                    listOf(text(TermsSpan("uno")), text(TermsSpan("dos")), text(TermsSpan("tres"))),
                ),
            ),
            blocks,
        )
    }

    @Test
    fun `una lista pegada a un parrafo los separa en bloques distintos`() {
        val blocks = parse("Esta prohibido:\n- mentir\n- robar\nFin.")

        assertEquals(
            listOf(
                TermsBlock.Paragraph(text(TermsSpan("Esta prohibido:"))),
                TermsBlock.Bullets(listOf(text(TermsSpan("mentir")), text(TermsSpan("robar")))),
                TermsBlock.Paragraph(text(TermsSpan("Fin."))),
            ),
            blocks,
        )
    }

    @Test
    fun `dos listas separadas por una linea en blanco son dos bloques`() {
        val blocks = parse("- a\n\n- b")

        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is TermsBlock.Bullets })
    }

    // -- Negritas -----------------------------------------------------------------------------

    @Test
    fun `una negrita en medio de un parrafo`() {
        val block = parse("No es un **servicio de emergencias** de ningun tipo.").single() as TermsBlock.Paragraph

        assertEquals(
            text(TermsSpan("No es un "), TermsSpan("servicio de emergencias", bold = true), TermsSpan(" de ningun tipo.")),
            block.spans,
        )
    }

    @Test
    fun `una negrita al principio de un item de lista`() {
        val block = parse("- **No es un servicio de emergencias.** Llama al 911.").single() as TermsBlock.Bullets

        assertEquals(
            text(TermsSpan("No es un servicio de emergencias.", bold = true), TermsSpan(" Llama al 911.")),
            block.items.single(),
        )
    }

    @Test
    fun `dos negritas en la misma linea`() {
        val block = parse("**a** y **b**").single() as TermsBlock.Paragraph

        assertEquals(
            text(TermsSpan("a", bold = true), TermsSpan(" y "), TermsSpan("b", bold = true)),
            block.spans,
        )
    }

    @Test
    fun `un doble asterisco sin cierre queda como texto literal, nunca se pierde`() {
        val block = parse("precio **sin cerrar").single() as TermsBlock.Paragraph

        assertEquals(text(TermsSpan("precio **sin cerrar")), block.spans)
    }

    // -- Tablas -------------------------------------------------------------------------------

    @Test
    fun `una tabla de tres columnas, sin la fila de separadores`() {
        val md = """
            | Dato | Para que | Como se guarda |
            |---|---|---|
            | DNI | Validar identidad | Cifrado |
            | Email | Enviar codigos | En la base |
        """.trimIndent()

        val table = parse(md).single() as TermsBlock.Table

        assertEquals(listOf("Dato", "Para que", "Como se guarda"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(
            listOf(text(TermsSpan("DNI")), text(TermsSpan("Validar identidad")), text(TermsSpan("Cifrado"))),
            table.rows[0],
        )
        assertEquals("Email", table.rows[1][0].single().text)
    }

    @Test
    fun `una tabla entre parrafos`() {
        val blocks = parse("Antes.\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\nDespues.")

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is TermsBlock.Paragraph)
        assertTrue(blocks[1] is TermsBlock.Table)
        assertTrue(blocks[2] is TermsBlock.Paragraph)
    }

    // -- Marcadores ---------------------------------------------------------------------------

    @Test
    fun `los marcadores se reemplazan en parrafos, titulos, listas y tablas`() {
        val md = "# T {{VERSION}}\n\nResp: {{RESPONSABLE}}.\n\n- mail {{EMAIL_CONTACTO}}\n\n| {{VERSION}} |\n|---|\n| {{DOMICILIO}} |"
        val values = mapOf(
            "VERSION" to "1.0",
            "RESPONSABLE" to "ACME SA",
            "EMAIL_CONTACTO" to "a@b.com",
            "DOMICILIO" to "Calle 1",
        )

        val blocks = parse(md, values)

        assertEquals(TermsBlock.Heading(1, "T 1.0"), blocks[0])
        assertEquals(text(TermsSpan("Resp: ACME SA.")), (blocks[1] as TermsBlock.Paragraph).spans)
        assertEquals(text(TermsSpan("mail a@b.com")), (blocks[2] as TermsBlock.Bullets).items.single())
        val table = blocks[3] as TermsBlock.Table
        assertEquals(listOf("1.0"), table.headers)
        assertEquals("Calle 1", table.rows.single().single().single().text)
    }

    @Test
    fun `un marcador desconocido queda tal cual`() {
        val block = parse("Hola {{OTRO}}").single() as TermsBlock.Paragraph

        assertEquals(text(TermsSpan("Hola {{OTRO}}")), block.spans)
    }

    @Test
    fun `un marcador dentro de una negrita se reemplaza y sigue siendo negrita`() {
        val block = parse("**{{RESPONSABLE}}**", mapOf("RESPONSABLE" to "ACME")).single() as TermsBlock.Paragraph

        assertEquals(text(TermsSpan("ACME", bold = true)), block.spans)
    }

    @Test
    fun `un valor configurado con markdown no altera la estructura del documento`() {
        val values = mapOf("RESPONSABLE" to "ACME **SA** | - # algo")

        val block = parse("Resp: {{RESPONSABLE}}.", values).single() as TermsBlock.Paragraph

        assertEquals(text(TermsSpan("Resp: ACME **SA** | - # algo.")), block.spans)
    }

    @Test
    fun `los marcadores de LegalInfo son los cuatro del contrato`() {
        val legal = LegalInfo(responsibleName = "R", contactEmail = "E", address = "D", version = "V")

        assertEquals(
            mapOf("RESPONSABLE" to "R", "EMAIL_CONTACTO" to "E", "DOMICILIO" to "D", "VERSION" to "V"),
            legal.placeholders(),
        )
    }
}
