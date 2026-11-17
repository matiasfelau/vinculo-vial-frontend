package ar.felaufernandezbottaro.vinculovial.data.edge.document

import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentScanStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Qué etapa del escaneo del DNI corresponde a lo que hay en el cuadro. */
class DocumentScanEvaluatorTest {

    private fun dni(procedure: String? = "00307513148") = DniBarcodeInterpreter.Reading.Dni(
        ArgentineDni(
            number = "12345678",
            surname = "GARCIA",
            givenNames = "JUAN",
            sex = "M",
            procedureNumber = procedure,
        ),
    )

    private val notADni = DniBarcodeInterpreter.Reading.NotADni
    private val unsupported = DniBarcodeInterpreter.Reading.UnsupportedDocument

    @Test
    fun `sin codigos sigue buscando`() {
        val state = DocumentScanEvaluator.evaluate(emptyList(), hasUndecodedCode = false)

        assertEquals(DocumentScanStage.SEARCHING, state.stage)
    }

    @Test
    fun `un DNI valido se decodifica con numero, sexo y tramite`() {
        val state = DocumentScanEvaluator.evaluate(listOf(dni()), hasUndecodedCode = false)

        assertEquals(DocumentScanStage.DECODED, state.stage)
        assertEquals("12345678", state.document?.number)
        assertEquals("M", state.document?.sex)
        assertEquals("00307513148", state.document?.procedureNumber)
    }

    @Test
    fun `un DNI sin numero de tramite no sirve`() {
        val state = DocumentScanEvaluator.evaluate(listOf(dni(procedure = null)), hasUndecodedCode = false)

        assertEquals(DocumentScanStage.MISSING_PROCEDURE_NUMBER, state.stage)
        assertNull(state.document)
    }

    @Test
    fun `entre varios codigos gana el que da un DNI utilizable`() {
        val state = DocumentScanEvaluator.evaluate(
            listOf(notADni, unsupported, dni()),
            hasUndecodedCode = true,
        )

        assertEquals(DocumentScanStage.DECODED, state.stage)
    }

    @Test
    fun `un DNI electronico se senala como documento no soportado`() {
        val state = DocumentScanEvaluator.evaluate(listOf(notADni, unsupported), hasUndecodedCode = false)

        assertEquals(DocumentScanStage.UNSUPPORTED_DOCUMENT, state.stage)
    }

    @Test
    fun `un codigo detectado pero no decodificado se informa como codigo detectado`() {
        val state = DocumentScanEvaluator.evaluate(listOf(notADni), hasUndecodedCode = true)

        assertEquals(DocumentScanStage.CODE_DETECTED, state.stage)
    }

    @Test
    fun `un codigo decodificado que no es de un DNI se informa como tal`() {
        val state = DocumentScanEvaluator.evaluate(listOf(notADni), hasUndecodedCode = false)

        assertEquals(DocumentScanStage.NOT_A_DNI, state.stage)
    }
}
