package ar.felaufernandezbottaro.vinculovial.data.edge.document

import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentIdentity
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentScanStage
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentScanState

/**
 * La decisión de qué etapa del escaneo del DNI corresponde a lo que había en el cuadro, sin ML Kit:
 * [MlKitDocumentScanSession] traduce cada código a una [DniBarcodeInterpreter.Reading] y delega acá.
 *
 * El orden importa: entre varios códigos en el cuadro gana el que dé un DNI utilizable. Recién si
 * ninguno sirve se reporta el motivo más informativo de los que haya.
 */
internal object DocumentScanEvaluator {

    /**
     * @param readings la lectura de cada código del cuadro, en el orden en que llegaron.
     * @param hasUndecodedCode `true` si algún código fue detectado pero todavía no decodificado
     * (`rawValue` nulo, que es lo que reporta `enableAllPotentialBarcodes()`).
     */
    fun evaluate(readings: List<DniBarcodeInterpreter.Reading>, hasUndecodedCode: Boolean): DocumentScanState {
        if (readings.isEmpty()) return DocumentScanState(DocumentScanStage.SEARCHING)

        readings.filterIsInstance<DniBarcodeInterpreter.Reading.Dni>().forEach { reading ->
            val procedureNumber = reading.value.procedureNumber
                ?: return DocumentScanState(DocumentScanStage.MISSING_PROCEDURE_NUMBER)

            return DocumentScanState(
                stage = DocumentScanStage.DECODED,
                document = DocumentIdentity(
                    number = reading.value.number,
                    sex = reading.value.sex,
                    procedureNumber = procedureNumber,
                ),
            )
        }

        return when {
            readings.any { it is DniBarcodeInterpreter.Reading.UnsupportedDocument } ->
                DocumentScanState(DocumentScanStage.UNSUPPORTED_DOCUMENT)

            // Hay un código en el cuadro y todavía no se pudo leer: ML Kit ya está pidiendo zoom
            // por su cuenta; acá sólo se refleja en la UI para que el usuario lo sepa.
            hasUndecodedCode -> DocumentScanState(DocumentScanStage.CODE_DETECTED)

            else -> DocumentScanState(DocumentScanStage.NOT_A_DNI)
        }
    }
}
