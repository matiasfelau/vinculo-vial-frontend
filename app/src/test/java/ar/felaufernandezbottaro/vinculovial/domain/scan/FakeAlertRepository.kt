package ar.felaufernandezbottaro.vinculovial.domain.scan

import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidencePhotoSet
import kotlinx.coroutines.CompletableDeferred

/** Un envío recibido por [FakeAlertRepository]. */
data class SentAlert(
    val attemptId: String,
    val patEnc: String,
    val alertType: AlertType,
    val evidence: EvidencePhotoSet,
)

/**
 * Repositorio falso: registra los envíos y devuelve, en orden, los resultados programados en
 * [queue]; cuando la cola se vacía devuelve [result].
 */
class FakeAlertRepository(
    var result: SendAlertResult = SendAlertResult.Sent,
) : AlertRepository {

    val queue = ArrayDeque<SendAlertResult>()
    val received = mutableListOf<SentAlert>()

    /** Si no es `null`, cada envío espera a que se complete: sirve para probar envíos en vuelo. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun sendAlert(
        attemptId: String,
        patEnc: String,
        alertType: AlertType,
        evidence: EvidencePhotoSet,
    ): SendAlertResult {
        received += SentAlert(attemptId, patEnc, alertType, evidence)
        gate?.await()
        return queue.removeFirstOrNull() ?: result
    }
}
