package ar.felaufernandezbottaro.vinculovial.data.inbox

import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.InboxPageDto
import retrofit2.Response

class FakeInboxApi : InboxApi {

    var response: Response<InboxPageDto> = Response.success(InboxPageDto(items = emptyList(), nextCursor = null))
    var exceptionToThrow: Throwable? = null

    var lastAuthorization: String? = null
        private set
    var lastCursor: String? = null
        private set
    var callCount = 0
        private set

    override suspend fun getInbox(authorization: String, cursor: String?): Response<InboxPageDto> {
        callCount++
        lastAuthorization = authorization
        lastCursor = cursor
        exceptionToThrow?.let { throw it }
        return response
    }
}
