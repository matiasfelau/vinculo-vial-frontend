package ar.felaufernandezbottaro.vinculovial.data.account

import ar.felaufernandezbottaro.vinculovial.data.account.dto.DeleteAccountRequestDto
import retrofit2.Response

class FakeAccountApi : AccountApi {

    var response: Response<Unit> = Response.success(204, Unit)
    var exceptionToThrow: Throwable? = null

    /** Efecto secundario de la llamada, antes de responder: lo que hace el `TokenAuthenticator` en la vida real. */
    var onCall: () -> Unit = {}

    var lastAuthorization: String? = null
        private set
    var lastBody: DeleteAccountRequestDto? = null
        private set
    var callCount = 0
        private set

    override suspend fun deleteAccount(authorization: String, body: DeleteAccountRequestDto): Response<Unit> {
        callCount++
        lastAuthorization = authorization
        lastBody = body
        onCall()
        exceptionToThrow?.let { throw it }
        return response
    }
}
