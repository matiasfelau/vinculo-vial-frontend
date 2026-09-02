package ar.felaufernandezbottaro.vinculovial.domain.inbox

/** Repositorio falso: registra los cursores recibidos y devuelve lo programado. */
class FakeInboxRepository(
    var result: GetInboxResult = GetInboxResult.Success(InboxPage(items = emptyList(), nextCursor = null)),
) : InboxRepository {

    val receivedCursors = mutableListOf<String?>()

    override suspend fun getInbox(cursor: String?): GetInboxResult {
        receivedCursors += cursor
        return result
    }
}
