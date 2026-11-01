package ar.felaufernandezbottaro.vinculovial.data.inbox

import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.FeedbackRequestDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.InboxPageDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.ReportRequestDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.SentPageDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
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

    // -- Evidencia, valoración y reporte ----------------------------------------------------

    var evidenceResponse: Response<ResponseBody> = Response.success(byteArrayOf(1, 2, 3).toResponseBody("image/jpeg".toMediaType()))
    var feedbackResponse: Response<Unit> = Response.success(204, Unit)
    var reportResponse: Response<Unit> = Response.success(202, Unit)

    var lastEvidenceCall: Triple<String, String, Int>? = null
        private set
    var lastFeedback: Pair<String, FeedbackRequestDto>? = null
        private set
    var lastReport: Pair<String, ReportRequestDto>? = null
        private set

    override suspend fun getEvidence(
        authorization: String,
        alertId: String,
        role: String,
        index: Int,
    ): Response<ResponseBody> {
        callCount++
        lastAuthorization = authorization
        lastEvidenceCall = Triple(alertId, role, index)
        exceptionToThrow?.let { throw it }
        return evidenceResponse
    }

    override suspend fun putFeedback(authorization: String, alertId: String, body: FeedbackRequestDto): Response<Unit> {
        callCount++
        lastAuthorization = authorization
        lastFeedback = alertId to body
        exceptionToThrow?.let { throw it }
        return feedbackResponse
    }

    override suspend fun reportSender(authorization: String, alertId: String, body: ReportRequestDto): Response<Unit> {
        callCount++
        lastAuthorization = authorization
        lastReport = alertId to body
        exceptionToThrow?.let { throw it }
        return reportResponse
    }

    // -- Enviados y vistas ------------------------------------------------------------------

    var sentResponse: Response<SentPageDto> = Response.success(SentPageDto(items = emptyList(), nextCursor = null))
    var seenResponse: Response<Unit> = Response.success(204, Unit)

    var lastSentCursor: String? = null
        private set
    var lastSeenAlertId: String? = null
        private set

    override suspend fun getSent(authorization: String, cursor: String?): Response<SentPageDto> {
        callCount++
        lastAuthorization = authorization
        lastSentCursor = cursor
        exceptionToThrow?.let { throw it }
        return sentResponse
    }

    override suspend fun markSeen(authorization: String, alertId: String): Response<Unit> {
        callCount++
        lastAuthorization = authorization
        lastSeenAlertId = alertId
        exceptionToThrow?.let { throw it }
        return seenResponse
    }
}
