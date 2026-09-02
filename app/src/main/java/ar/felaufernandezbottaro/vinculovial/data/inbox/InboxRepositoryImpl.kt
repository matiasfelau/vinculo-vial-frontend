package ar.felaufernandezbottaro.vinculovial.data.inbox

import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.AlertItemDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.InboxPageDto
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxPage
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consulta `/alerts/inbox` con el Access Token: sin HPKE ni attestation, es una lectura
 * autenticada simple -- ver el contrato compartido en CLAUDE.md.
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
                response.code() == HTTP_TOO_MANY_REQUESTS -> GetInboxResult.Failed(InboxError.RateLimited)
                else -> GetInboxResult.Failed(InboxError.Unexpected)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            GetInboxResult.Failed(InboxError.Network)
        } catch (e: RuntimeException) {
            GetInboxResult.Failed(InboxError.Unexpected)
        }
    }

    private fun InboxPageDto.toDomain(): InboxPage =
        InboxPage(items = items.map { it.toDomain() }, nextCursor = nextCursor)

    private fun AlertItemDto.toDomain(): AlertItem =
        AlertItem(id = id, alertType = alertType, createdAt = Instant.parse(createdAt))

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
