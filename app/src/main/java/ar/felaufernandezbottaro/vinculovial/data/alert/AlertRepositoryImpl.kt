package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.SendAlertRequestDto
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertRepository
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publica la alerta con el `pat_enc` que ya trae [sendAlert] -- sin `HpkeEncryptor` entre las
 * dependencias, esta clase no tiene forma de generar un cifrado nuevo.
 */
@Singleton
class AlertRepositoryImpl @Inject constructor(
    private val api: AlertApi,
    private val tokenStore: SecureTokenStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : AlertRepository {

    override suspend fun sendAlert(alertType: AlertType, patEnc: String): SendAlertResult = withContext(dispatcher) {
        val accessToken = tokenStore.read()?.accessToken
            ?: return@withContext SendAlertResult.Failed(SendAlertError.Unexpected)

        try {
            val response = api.sendAlert(
                BEARER_PREFIX + accessToken,
                SendAlertRequestDto(alertType = alertType.wireValue, patEnc = patEnc),
            )

            when {
                response.isSuccessful -> SendAlertResult.Sent
                response.code() == HTTP_BAD_REQUEST -> SendAlertResult.Failed(SendAlertError.InvalidData)
                response.code() == HTTP_TOO_MANY_REQUESTS -> SendAlertResult.Failed(SendAlertError.RateLimited)
                else -> SendAlertResult.Failed(SendAlertError.Unexpected)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            SendAlertResult.Failed(SendAlertError.Network)
        } catch (e: RuntimeException) {
            SendAlertResult.Failed(SendAlertError.Unexpected)
        }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
