package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.VehicleCheckRequestDto
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoConfigurationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.crypto.toCryptogram
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.di.VehicleHpkePublicKey
import ar.felaufernandezbottaro.vinculovial.domain.scan.CheckVehicleError
import ar.felaufernandezbottaro.vinculovial.domain.scan.CheckVehicleResult
import ar.felaufernandezbottaro.vinculovial.domain.scan.VehicleCheckRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifra la patente contra la Public Key de Vehicle y consulta `/vehicles/check`. El `pat_enc`
 * resultante viaja en [CheckVehicleResult.Found] para que `SendAlertUseCase` lo reuse sin
 * volver a cifrar -- ver el flujo de dos pasos en CLAUDE.md.
 */
@Singleton
class VehicleCheckRepositoryImpl @Inject constructor(
    private val api: VehicleCheckApi,
    private val encryptor: HpkeEncryptor,
    @VehicleHpkePublicKey private val vehiclePublicKey: String,
    private val tokenStore: SecureTokenStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : VehicleCheckRepository {

    override suspend fun checkVehicle(patente: String): CheckVehicleResult = withContext(dispatcher) {
        val accessToken = tokenStore.read()?.accessToken
            ?: return@withContext CheckVehicleResult.Failed(CheckVehicleError.Unexpected)

        val patEnc = try {
            encryptPatente(patente)
        } catch (e: CryptoConfigurationException) {
            return@withContext CheckVehicleResult.Failed(CheckVehicleError.Configuration)
        } catch (e: CryptoOperationException) {
            return@withContext CheckVehicleResult.Failed(CheckVehicleError.Unexpected)
        }

        try {
            val response = api.checkVehicle(BEARER_PREFIX + accessToken, VehicleCheckRequestDto(patEnc))

            when {
                response.isSuccessful -> CheckVehicleResult.Found(patEnc)
                response.code() == HTTP_NOT_FOUND -> CheckVehicleResult.NotInNetwork
                response.code() == HTTP_TOO_MANY_REQUESTS -> CheckVehicleResult.Failed(CheckVehicleError.RateLimited)
                else -> CheckVehicleResult.Failed(CheckVehicleError.Unexpected)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            CheckVehicleResult.Failed(CheckVehicleError.Network)
        } catch (e: RuntimeException) {
            CheckVehicleResult.Failed(CheckVehicleError.Unexpected)
        }
    }

    /**
     * Sin envoltorio JSON: a diferencia del `payload_enc` de US-04 (varios campos), acá se
     * cifra directamente el texto de la patente -- ver el paso 1 del flujo en CLAUDE.md.
     */
    private fun encryptPatente(patente: String): String {
        val plaintext = patente.toByteArray(Charsets.UTF_8)
        return try {
            encryptor.encrypt(plaintext, vehiclePublicKey).toCryptogram()
        } finally {
            plaintext.fill(0)
        }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
