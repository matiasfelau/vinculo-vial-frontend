package ar.felaufernandezbottaro.vinculovial.data.vehicle

import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoConfigurationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.crypto.toCryptogram
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.LinkVehicleRequestDto
import ar.felaufernandezbottaro.vinculovial.di.IdentityHpkePublicKey
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.di.VehicleHpkePublicKey
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleResult
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleRepository
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifra DNI y patente cada uno contra su destinatario -- cifrado diferenciado de US-04,
 * nunca la misma clave para los dos campos -- y publica el vínculo en el Gateway con el
 * Access Token activo.
 *
 * Traduce el mundo HTTP a errores de dominio: `presentation` nunca ve un código de estado
 * ni una excepción de red.
 */
@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val api: VehicleApi,
    private val encryptor: HpkeEncryptor,
    @IdentityHpkePublicKey private val identityPublicKey: String,
    @VehicleHpkePublicKey private val vehiclePublicKey: String,
    private val tokenStore: SecureTokenStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : VehicleRepository {

    override suspend fun linkVehicle(cedula: CedulaData): LinkVehicleResult = withContext(dispatcher) {
        // Detrás de una sesión activa esto no debería pasar (el NavHost ya redirige a Login
        // sin sesión) -- pero un fallo de lectura nunca debe traducirse en un crash.
        val accessToken = tokenStore.read()?.accessToken
            ?: return@withContext LinkVehicleResult.Failed(LinkVehicleError.Unexpected)

        val request = try {
            LinkVehicleRequestDto(
                dniEnc = encryptor.encrypt(cedula.dni.toByteArray(), identityPublicKey).toCryptogram(),
                patEnc = encryptor.encrypt(cedula.patente.toByteArray(), vehiclePublicKey).toCryptogram(),
            )
        } catch (e: CryptoConfigurationException) {
            return@withContext LinkVehicleResult.Failed(LinkVehicleError.Configuration)
        } catch (e: CryptoOperationException) {
            return@withContext LinkVehicleResult.Failed(LinkVehicleError.Unexpected)
        }

        try {
            val response = api.linkVehicle(BEARER_PREFIX + accessToken, request)

            when {
                response.isSuccessful -> LinkVehicleResult.Linked
                response.code() == HTTP_BAD_REQUEST -> LinkVehicleResult.Failed(LinkVehicleError.InvalidData)
                response.code() == HTTP_FORBIDDEN -> LinkVehicleResult.Failed(LinkVehicleError.OwnerMismatch)
                response.code() == HTTP_CONFLICT -> LinkVehicleResult.Failed(LinkVehicleError.AlreadyLinked)
                else -> LinkVehicleResult.Failed(LinkVehicleError.Unexpected)
            }
        } catch (e: CancellationException) {
            // El usuario abandonó la pantalla: cancelar no es un fallo de la vinculación.
            // Va antes que RuntimeException porque hereda de IllegalStateException.
            throw e
        } catch (e: IOException) {
            // Sin conexión, DNS, timeout, TLS.
            LinkVehicleResult.Failed(LinkVehicleError.Network)
        } catch (e: RuntimeException) {
            // Respuesta ilegible o cualquier fallo inesperado del stack HTTP.
            LinkVehicleResult.Failed(LinkVehicleError.Unexpected)
        }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_FORBIDDEN = 403
        const val HTTP_CONFLICT = 409
    }
}
