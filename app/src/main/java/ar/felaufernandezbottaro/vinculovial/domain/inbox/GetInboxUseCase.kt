package ar.felaufernandezbottaro.vinculovial.domain.inbox

import javax.inject.Inject

class GetInboxUseCase @Inject constructor(private val repository: InboxRepository) {
    suspend operator fun invoke(cursor: String? = null): GetInboxResult = repository.getInbox(cursor)
}
