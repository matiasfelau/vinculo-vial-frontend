package ar.felaufernandezbottaro.vinculovial.domain.account

import kotlinx.coroutines.CompletableDeferred

class FakeAccountRepository : AccountRepository {

    var result: DeleteAccountResult = DeleteAccountResult.Deleted

    /** Si se setea, la llamada queda suspendida hasta que se complete: simula una request en vuelo. */
    var gate: CompletableDeferred<Unit>? = null

    val receivedPasswords = mutableListOf<String>()
    val callCount: Int get() = receivedPasswords.size

    override suspend fun deleteAccount(password: String): DeleteAccountResult {
        receivedPasswords.add(password)
        gate?.await()
        return result
    }
}

class FakeLocalAccountDataEraser : LocalAccountDataEraser {

    /** Si se setea, la limpieza queda suspendida hasta que se complete. */
    var gate: CompletableDeferred<Unit>? = null

    var startedCount = 0
        private set
    var completedCount = 0
        private set

    override suspend fun eraseAll() {
        startedCount++
        gate?.await()
        completedCount++
    }
}
