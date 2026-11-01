package ar.felaufernandezbottaro.vinculovial.data.inbox

import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.AlertItemDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.FeedbackRequestDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.InboxPageDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.ReportRequestDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.SentAlertDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.SentPageDto
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.remote.isTransientUpstreamFailure
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceRef
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackRating
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetSentResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxPage
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxRepository
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportReason
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SeenResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlert
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlertStatus
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consulta `/alerts/inbox` y opera sobre las alertas del dueño (evidencia, valoración, reporte)
 * con el Access Token: sin HPKE, lecturas y escrituras autenticadas simples -- ver el contrato
 * compartido en CLAUDE.md.
 */
@Singleton
class InboxRepositoryImpl @Inject constructor(
    private val api: InboxApi,
    private val tokenStore: SecureTokenStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : InboxRepository {

    override suspend fun getInbox(cursor: String?): GetInboxResult = withContext(dispatcher) {
        val accessToken = tokenStore.read()?.accessToken
            ?: return@withContext GetInboxResult.Failed(InboxError.Unexpected)

        try {
            val response = api.getInbox(BEARER_PREFIX + accessToken, cursor)
            val body = response.body()

            when {
                response.isSuccessful && body != null -> GetInboxResult.Success(body.toDomain())
                else -> GetInboxResult.Failed(response.code().toInboxError())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CircuitBreakerOpenException) {
            GetInboxResult.Failed(InboxError.Unavailable)
        } catch (e: IOException) {
            GetInboxResult.Failed(InboxError.Network)
        } catch (e: RuntimeException) {
            GetInboxResult.Failed(InboxError.Unexpected)
        }
    }

    override suspend fun getSent(cursor: String?): GetSentResult =
        call(
            onFailure = { GetSentResult.Failed(it) },
        ) { token ->
            val response = api.getSent(BEARER_PREFIX + token, cursor)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                GetSentResult.Success(body.toDomain())
            } else {
                GetSentResult.Failed(response.code().toInboxError())
            }
        }

    override suspend fun markSeen(alertId: String): SeenResult =
        call(
            onFailure = { SeenResult.Failed(it) },
        ) { token ->
            val response = api.markSeen(BEARER_PREFIX + token, alertId)
            if (response.isSuccessful) SeenResult.Marked else SeenResult.Failed(response.code().toInboxError())
        }

    override suspend fun getEvidence(alertId: String, role: String, index: Int): EvidenceResult =
        call(
            onFailure = { EvidenceResult.Failed(it) },
        ) { token ->
            val response = api.getEvidence(BEARER_PREFIX + token, alertId, role, index)
            val bytes = if (response.isSuccessful) response.body()?.bytes() else null
            when {
                bytes != null -> EvidenceResult.Loaded(bytes)
                response.code() == HTTP_GONE -> EvidenceResult.Expired
                else -> EvidenceResult.Failed(response.code().toInboxError())
            }
        }

    override suspend fun setFeedback(alertId: String, rating: FeedbackRating): FeedbackResult =
        call(
            onFailure = { FeedbackResult.Failed(it) },
        ) { token ->
            val response = api.putFeedback(BEARER_PREFIX + token, alertId, FeedbackRequestDto(rating.name))
            if (response.isSuccessful) FeedbackResult.Saved else FeedbackResult.Failed(response.code().toInboxError())
        }

    override suspend fun reportSender(alertId: String, reason: ReportReason): ReportResult =
        call(
            onFailure = { ReportResult.Failed(it) },
        ) { token ->
            val response: Response<Unit> = api.reportSender(BEARER_PREFIX + token, alertId, ReportRequestDto(reason.name))
            when {
                response.isSuccessful -> ReportResult.Reported
                response.code() == HTTP_CONFLICT -> ReportResult.AlreadyReported
                else -> ReportResult.Failed(response.code().toInboxError())
            }
        }

    /** Sesión, hilo de IO y traducción de excepciones: lo común a las tres operaciones sobre una alerta. */
    private suspend fun <R> call(
        onFailure: (InboxError) -> R,
        block: suspend (accessToken: String) -> R,
    ): R = withContext(dispatcher) {
        val accessToken = tokenStore.read()?.accessToken ?: return@withContext onFailure(InboxError.Unexpected)

        try {
            block(accessToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CircuitBreakerOpenException) {
            onFailure(InboxError.Unavailable)
        } catch (e: IOException) {
            onFailure(InboxError.Network)
        } catch (e: RuntimeException) {
            onFailure(InboxError.Unexpected)
        }
    }

    private fun Int.toInboxError(): InboxError = when {
        this == HTTP_NOT_FOUND -> InboxError.NotFound
        this == HTTP_TOO_MANY_REQUESTS -> InboxError.RateLimited
        isTransientUpstreamFailure() -> InboxError.Unavailable
        else -> InboxError.Unexpected
    }

    private fun InboxPageDto.toDomain(): InboxPage =
        InboxPage(items = items.map { it.toDomain() }, nextCursor = nextCursor)

    /**
     * Un `status` que la app no conoce se descarta en vez de adivinar: mostrarlo como "En
     * validación" o "Validada" sería afirmar algo que no se sabe.
     */
    private fun SentPageDto.toDomain(): SentPage =
        SentPage(items = items.mapNotNull { it.toDomainOrNull() }, nextCursor = nextCursor)

    private fun SentAlertDto.toDomainOrNull(): SentAlert? {
        val parsedStatus = SentAlertStatus.entries.firstOrNull { it.name == status } ?: return null
        return SentAlert(
            id = id,
            alertType = alertType,
            createdAt = Instant.parse(createdAt),
            status = parsedStatus,
            seenAt = seenAt?.toInstantOrNull(),
        )
    }

    private fun AlertItemDto.toDomain(): AlertItem = AlertItem(
        id = id,
        alertType = alertType,
        createdAt = Instant.parse(createdAt),
        requiresReview = requiresReview,
        evidence = evidence.orEmpty().map { EvidenceRef(role = it.role, index = it.index) },
        evidenceExpiresAt = evidenceExpiresAt?.toInstantOrNull(),
        myFeedback = myFeedback?.let { value -> FeedbackRating.entries.firstOrNull { it.name == value } },
        reported = reported,
        seen = seen,
    )

    /** Un `evidence_expires_at` ilegible no tumba la lista: es informativo. */
    private fun String.toInstantOrNull(): Instant? = try {
        OffsetDateTime.parse(this).toInstant()
    } catch (e: DateTimeParseException) {
        null
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_GONE = 410
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
