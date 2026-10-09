package ar.felaufernandezbottaro.vinculovial.domain.inbox

import javax.inject.Inject

class GetEvidenceUseCase @Inject constructor(private val repository: InboxRepository) {
    suspend operator fun invoke(alertId: String, ref: EvidenceRef): EvidenceResult =
        repository.getEvidence(alertId, ref.role, ref.index)
}

class SetFeedbackUseCase @Inject constructor(private val repository: InboxRepository) {
    suspend operator fun invoke(alertId: String, rating: FeedbackRating): FeedbackResult =
        repository.setFeedback(alertId, rating)
}

class ReportSenderUseCase @Inject constructor(private val repository: InboxRepository) {
    suspend operator fun invoke(alertId: String, reason: ReportReason): ReportResult =
        repository.reportSender(alertId, reason)
}
