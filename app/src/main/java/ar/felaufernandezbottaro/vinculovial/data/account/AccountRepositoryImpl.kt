package ar.felaufernandezbottaro.vinculovial.data.account

import ar.felaufernandezbottaro.vinculovial.data.account.dto.DeleteAccountRequestDto
import ar.felaufernandezbottaro.vinculovial.data.alert.apiErrorCode
import ar.felaufernandezbottaro.vinculovial.data.alert.dto.ApiErrorDto
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.remote.isTransientUpstreamFailure
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.domain.account.AccountRepository
import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountError
import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountResult
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `DELETE /api/v1/users/me` (US-11) es un endpoint autenticado por JWT, sin attestation -- mismo
 * criterio que las rutas de alertas. Comparte el cliente con `TokenAuthenticator`: un access
 * token vencido se refresca solo antes de llegar acá, y el 401 que sí llega hasta este punto
 * es o `INVALID_PASSWORD` o una sesión que no se pudo recuperar.
 *
 * Esta clase sólo traduce la respuesta; no toca el estado local. Borrarlo es responsabilidad de
 * `DeleteAccountUseCase`, y sólo ante un [DeleteAccountResult.Deleted].
 */
@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val api: AccountApi,
    private val tokenStore: SecureTokenStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    moshi: Moshi,
) : AccountRepository {

    private val errorAdapter = moshi.adapter(ApiErrorDto::class.java)

    override suspend fun deleteAccount(password: String): DeleteAccountResult = withContext(dispatcher) {
        val accessToken = tokenStore.read()?.accessToken
            ?: return@withContext DeleteAccountResult.Failed(DeleteAccountError.SessionExpired)

        try {
            api.deleteAccount(BEARER_PREFIX + accessToken, DeleteAccountRequestDto(password)).toResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CircuitBreakerOpenException) {
            DeleteAccountResult.Failed(DeleteAccountError.Unavailable)
        } catch (e: IOException) {
            DeleteAccountResult.Failed(DeleteAccountError.Network)
        } catch (e: RuntimeException) {
            DeleteAccountResult.Failed(DeleteAccountError.Unexpected)
        }
    }

    private fun Response<Unit>.toResult(): DeleteAccountResult {
        val status = code()
        return when {
            isSuccessful -> DeleteAccountResult.Deleted
            status == HTTP_UNAUTHORIZED -> DeleteAccountResult.Failed(unauthorizedError())
            status == HTTP_TOO_MANY_REQUESTS -> DeleteAccountResult.Failed(DeleteAccountError.RateLimited)
            status.isTransientUpstreamFailure() -> DeleteAccountResult.Failed(DeleteAccountError.Unavailable)
            else -> DeleteAccountResult.Failed(DeleteAccountError.Unexpected)
        }
    }

    /**
     * El `code` es lo único que distingue una contraseña incorrecta (Identity) de una sesión
     * vencida (Kong, sin `code`). En el segundo caso el 401 sólo llega hasta acá si el
     * `TokenAuthenticator` ya no pudo recuperar la sesión: si el refresh la borró, es
     * [DeleteAccountError.SessionExpired]; si sigue intacta, el refresh falló por algo
     * transitorio (red, 5xx) y el usuario puede reintentar.
     */
    private fun Response<Unit>.unauthorizedError(): DeleteAccountError = when {
        apiErrorCode(errorAdapter) == CODE_INVALID_PASSWORD -> DeleteAccountError.InvalidPassword
        tokenStore.hasSession() -> DeleteAccountError.Unavailable
        else -> DeleteAccountError.SessionExpired
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val CODE_INVALID_PASSWORD = "INVALID_PASSWORD"
    }
}
