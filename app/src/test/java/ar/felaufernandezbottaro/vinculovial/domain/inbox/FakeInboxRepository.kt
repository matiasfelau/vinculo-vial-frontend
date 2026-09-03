package ar.felaufernandezbottaro.vinculovial.domain.inbox

import kotlinx.coroutines.CompletableDeferred

/**
 * Repositorio falso: registra los cursores recibidos y devuelve lo programado. [gate], si se
 * asigna, suspende la respuesta hasta que el test lo complete -- necesario para ejercitar la
 * guarda de reentrada, que de otro modo no tiene forma de observarse en un `UnconfinedTestDispatcher`
 * sin un punto de suspensión real.
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
}
