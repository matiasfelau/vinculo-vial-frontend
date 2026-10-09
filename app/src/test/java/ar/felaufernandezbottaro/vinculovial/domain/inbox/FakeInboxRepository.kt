package ar.felaufernandezbottaro.vinculovial.domain.inbox

import kotlinx.coroutines.CompletableDeferred

/**
 * Repositorio falso: registra lo recibido y devuelve lo programado. Los `gate`, si se asignan,
 * suspenden la respuesta hasta que el test los complete -- necesario para ejercitar las guardas
 * de reentrada y el estado optimista, que de otro modo no tienen forma de observarse en un
 * `UnconfinedTestDispatcher` sin un punto de suspensión real.
 */
class FakeInboxRepository(
    var result: GetInboxResult = GetInboxResult.Success(InboxPage(items = emptyList(), nextCursor = null)),
) : InboxRepository {

    var gate: CompletableDeferred<Unit>? = null

    val receivedCursors = mutableListOf<String?>()

    override suspend fun getInbox(cursor: String?): GetInboxResult {
        receivedCursors += cursor
        gate?.await()
        return result
    }

    // -- Evidencia ---------------------------------------------------------------------------

    /** Lo que devuelve [getEvidence] por foto; por defecto una foto nueva por llamada. */
    var evidenceResult: (EvidenceRef) -> EvidenceResult = { EvidenceResult.Loaded(ByteArray(8) { 7 }.also { loadedArrays += it }) }
    var evidenceGate: CompletableDeferred<Unit>? = null

    val evidenceCalls = mutableListOf<Triple<String, String, Int>>()

    /** Los arreglos que se entregaron en `Loaded`: sirven para comprobar que se sobreescribieron. */
    val loadedArrays = mutableListOf<ByteArray>()

    override suspend fun getEvidence(alertId: String, role: String, index: Int): EvidenceResult {
        evidenceCalls += Triple(alertId, role, index)
        evidenceGate?.await()
        return evidenceResult(EvidenceRef(role, index))
    }

    // -- Valoración --------------------------------------------------------------------------

    var feedbackResult: FeedbackResult = FeedbackResult.Saved
    var feedbackGate: CompletableDeferred<Unit>? = null

    val feedbackCalls = mutableListOf<Pair<String, FeedbackRating>>()

    override suspend fun setFeedback(alertId: String, rating: FeedbackRating): FeedbackResult {
        feedbackCalls += alertId to rating
        feedbackGate?.await()
        return feedbackResult
    }

    // -- Reporte -----------------------------------------------------------------------------

    var reportResult: ReportResult = ReportResult.Reported
    var reportGate: CompletableDeferred<Unit>? = null

    val reportCalls = mutableListOf<Pair<String, ReportReason>>()

    override suspend fun reportSender(alertId: String, reason: ReportReason): ReportResult {
        reportCalls += alertId to reason
        reportGate?.await()
        return reportResult
    }
}
