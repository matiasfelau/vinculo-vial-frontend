package ar.felaufernandezbottaro.vinculovial.data.benefits

import ar.felaufernandezbottaro.vinculovial.data.alert.apiErrorCode
import ar.felaufernandezbottaro.vinculovial.data.alert.dto.ApiErrorDto
import ar.felaufernandezbottaro.vinculovial.data.benefits.dto.BenefitDto
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.remote.isTransientUpstreamFailure
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.domain.benefits.Benefit
import ar.felaufernandezbottaro.vinculovial.domain.benefits.BenefitError
import ar.felaufernandezbottaro.vinculovial.domain.benefits.BenefitsRepository
import ar.felaufernandezbottaro.vinculovial.domain.benefits.ClaimResult
import ar.felaufernandezbottaro.vinculovial.domain.benefits.GetBenefitsResult
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lista, banners y `claim` de beneficios con el Access Token, sin HPKE: el código viaja en la
 * respuesta sobre TLS y nunca se loguea. Los banners se guardan en memoria por `ETag`: nada va
 * a disco y no se suma ninguna librería de imágenes.
 */
@Singleton
class BenefitsRepositoryImpl @Inject constructor(
    private val api: BenefitsApi,
    private val tokenStore: SecureTokenStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    moshi: Moshi,
) : BenefitsRepository {

    private val errorAdapter = moshi.adapter(ApiErrorDto::class.java)

    private class CachedBanner(val etag: String, val bytes: ByteArray)

    private val banners = ConcurrentHashMap<String, CachedBanner>()

    override suspend fun getBenefits(): GetBenefitsResult = withContext(dispatcher) {
        val accessToken = tokenStore.read()?.accessToken ?: return@withContext GetBenefitsResult.Failed

        try {
            val response = api.getBenefits(BEARER_PREFIX + accessToken)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                // Un elemento que no se puede leer se descarta solo: no tumba al resto de la lista.
                GetBenefitsResult.Success(body.mapNotNull { it.toDomain() })
            } else {
                GetBenefitsResult.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            GetBenefitsResult.Failed
        } catch (e: RuntimeException) {
            GetBenefitsResult.Failed
        }
    }

    override suspend fun getBanner(id: String): ByteArray? = withContext(dispatcher) {
        val accessToken = tokenStore.read()?.accessToken ?: return@withContext null
        val cached = banners[id]

        try {
            val response = api.getBanner(BEARER_PREFIX + accessToken, id, cached?.etag)
            when {
                response.code() == HTTP_NOT_MODIFIED -> cached?.bytes
                response.isSuccessful -> {
                    val bytes = response.body()?.bytes()
                    if (bytes == null || bytes.isEmpty() || bytes.size > MAX_BANNER_BYTES) {
                        null
                    } else {
                        response.headers()["ETag"]?.let { banners[id] = CachedBanner(it, bytes) }
                        bytes
                    }
                }
                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }

    override suspend fun claim(id: String): ClaimResult = withContext(dispatcher) {
        val accessToken = tokenStore.read()?.accessToken
            ?: return@withContext ClaimResult.Failed(BenefitError.Unexpected)

        try {
            val response = api.claim(BEARER_PREFIX + accessToken, id)
            val body = response.body()
            if (response.isSuccessful && body != null && body.code.isNotEmpty()) {
                ClaimResult.Success(body.code)
            } else {
                val status = response.code()
                val error = when {
                    response.isSuccessful -> BenefitError.Unexpected
                    status == HTTP_NOT_FOUND && response.apiErrorCode(errorAdapter) == CODE_NOT_FOUND -> BenefitError.NotFound
                    status == HTTP_CONFLICT && response.apiErrorCode(errorAdapter) == CODE_NO_CODES_LEFT -> BenefitError.NoCodesLeft
                    status == HTTP_TOO_MANY_REQUESTS -> BenefitError.RateLimited
                    status.isTransientUpstreamFailure() -> BenefitError.Unavailable
                    else -> BenefitError.Unexpected
                }
                ClaimResult.Failed(error)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CircuitBreakerOpenException) {
            ClaimResult.Failed(BenefitError.Unavailable)
        } catch (e: IOException) {
            ClaimResult.Failed(BenefitError.Network)
        } catch (e: RuntimeException) {
            ClaimResult.Failed(BenefitError.Unexpected)
        }
    }

    private fun BenefitDto.toDomain(): Benefit? =
        try {
            Benefit(
                id = id,
                partnerName = partnerName,
                title = title,
                description = description,
                terms = terms,
                openUrl = openUrl,
                endsAt = OffsetDateTime.parse(endsAt).toInstant(),
                code = code?.takeIf { it.isNotEmpty() },
            )
        } catch (e: DateTimeParseException) {
            null
        }

    private companion object {
        const val BEARER_PREFIX = "Bearer "

        /** El servidor limita el banner a 300 KB; un valor muy por encima no es un banner. */
        const val MAX_BANNER_BYTES = 1_048_576

        const val HTTP_NOT_MODIFIED = 304
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_TOO_MANY_REQUESTS = 429

        const val CODE_NOT_FOUND = "BENEFIT_NOT_FOUND"
        const val CODE_NO_CODES_LEFT = "NO_CODES_LEFT"
    }
}
