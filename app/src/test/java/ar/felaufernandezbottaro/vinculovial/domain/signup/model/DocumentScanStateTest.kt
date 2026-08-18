package ar.felaufernandezbottaro.vinculovial.domain.signup.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `isDone` es lo que hace avanzar el flujo: la pantalla navega cuando da `true`. Un falso
 * positivo mandaría al usuario a la selfie sin haber leído el documento — y sin documento el
 * registro se rechaza recién al final, después de toda la prueba de vida.
 */
class DocumentScanStateTest {

    private val documento =
        DocumentIdentity(number = "12345678", sex = "M", procedureNumber = "00307513148")

    @Test
    fun `esta listo solo con el codigo decodificado y el documento presente`() {
        val state = DocumentScanState(stage = DocumentScanStage.DECODED, document = documento)

        assertTrue(state.isDone)
    }

    @Test
    fun `DECODED sin documento no cuenta como listo`() {
        // Combinación que no debería construirse, pero si se construye no puede dejar
        // avanzar el flujo: `document` es lo único que efectivamente viaja al backend.
        val state = DocumentScanState(stage = DocumentScanStage.DECODED, document = null)

        assertFalse(state.isDone)
    }

    @Test
    fun `ningun estado intermedio deja avanzar`() {
        listOf(
            DocumentScanStage.SEARCHING,
            DocumentScanStage.CODE_DETECTED,
            DocumentScanStage.NOT_A_DNI,
            DocumentScanStage.UNSUPPORTED_DOCUMENT,
            DocumentScanStage.MISSING_PROCEDURE_NUMBER,
        ).forEach { stage ->
            assertFalse(
                "$stage no debería dejar avanzar",
                DocumentScanState(stage = stage, document = documento).isDone,
            )
        }
    }

    @Test
    fun `el estado inicial esta buscando y no deja avanzar`() {
        val inicial = DocumentScanState()

        assertTrue(inicial.stage == DocumentScanStage.SEARCHING)
        assertFalse(inicial.isDone)
    }
}
