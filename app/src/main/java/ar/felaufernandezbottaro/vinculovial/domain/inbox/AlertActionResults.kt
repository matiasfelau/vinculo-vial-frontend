package ar.felaufernandezbottaro.vinculovial.domain.inbox

/** Resultado de bajar una foto de evidencia redactada. */
sealed interface EvidenceResult {

    /**
     * Los bytes del JPEG. Se decodifican en memoria y quien los recibe los sobreescribe con
     * ceros al terminar: nunca se escriben a disco.
     */
    class Loaded(val bytes: ByteArray) : EvidenceResult {
        override fun toString(): String = "Loaded(bytes=${bytes.size})"
    }

    /** 410 `EVIDENCE_EXPIRED`: la retención venció. La UI muestra "La evidencia ya no está disponible". */
    data object Expired : EvidenceResult

    data class Failed(val error: InboxError) : EvidenceResult
}

/** Resultado de `PUT /alerts/{id}/feedback`. */
sealed interface FeedbackResult {

    /** 204. */
    data object Saved : FeedbackResult

    data class Failed(val error: InboxError) : FeedbackResult
}

/** Resultado de `POST /alerts/{id}/report`. */
sealed interface ReportResult {

    /** 202. */
    data object Reported : ReportResult

    /** 409 `ALREADY_REPORTED`: el usuario ya había reportado esta alerta. Para la UI es lo mismo que [Reported]. */
    data object AlreadyReported : ReportResult

    data class Failed(val error: InboxError) : ReportResult
}
