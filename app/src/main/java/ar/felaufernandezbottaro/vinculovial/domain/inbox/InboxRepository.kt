package ar.felaufernandezbottaro.vinculovial.domain.inbox

interface InboxRepository {

    /**
     * [cursor] es el valor opaco devuelto por la página anterior; ausente en la primera
     * carga. El `limit` nunca lo decide la app: siempre se usa el default del servidor.
     */
    suspend fun getInbox(cursor: String? = null): GetInboxResult

    /** Baja la foto redactada `(role, index)` de la alerta [alertId], como bytes en memoria. */
    suspend fun getEvidence(alertId: String, role: String, index: Int): EvidenceResult

    /** Una página de las alertas que envió el usuario. [cursor] es opaco, como el del buzón. */
    suspend fun getSent(cursor: String? = null): GetSentResult

    /** Avisa que el destinatario abrió el chat de [alertId]. Idempotente en el servidor. */
    suspend fun markSeen(alertId: String): SeenResult

    /** Guarda la valoración (upsert) del dueño a la validación de la alerta [alertId]. */
    suspend fun setFeedback(alertId: String, rating: FeedbackRating): FeedbackResult

    /** Reporta al emisor de la alerta [alertId]. */
    suspend fun reportSender(alertId: String, reason: ReportReason): ReportResult
}
