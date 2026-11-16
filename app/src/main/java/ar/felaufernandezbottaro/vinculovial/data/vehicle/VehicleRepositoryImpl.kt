package ar.felaufernandezbottaro.vinculovial.data.vehicle

import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoConfigurationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.crypto.toCryptogram
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.remote.isTransientUpstreamFailure
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehicleCountDto
import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehicleErrorBodyDto
import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehiclePayloadDto
import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehicleRequestDto
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.di.VehicleHpkePublicKey
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleRepository
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleResult
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.VehicleCount
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Buffer
import retrofit2.Response
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vincula, desvincula y cuenta los vehículos del usuario (US-14). La patente se empaqueta en un
 * payload JSON `{ "patente" }`, se cifra contra la Public Key de Vehicle y se publica en el Gateway
 * con el Access Token activo.
 *
 * Los errores se mapean por el `code` del backend, no por el status. Nunca se loguea ni se
 * devuelve una patente, y ninguna respuesta trae patentes: sólo el conteo.
 */
@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val api: VehicleApi,
    private val encryptor: HpkeEncryptor,
    @VehicleHpkePublicKey private val vehiclePublicKey: String,
    private val tokenStore: SecureTokenStore,
    moshi: Moshi,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : VehicleRepository {

    private val payloadAdapter: JsonAdapter<VehiclePayloadDto> = moshi.adapter(VehiclePayloadDto::class.java)
    private val errorAdapter: JsonAdapter<VehicleErrorBodyDto> = moshi.adapter(VehicleErrorBodyDto::class.java)

    override suspend fun getCount(): VehicleResult<VehicleCount> =
        execute({ authorization -> api.getCount(authorization) }, ::toCount)

    override suspend fun link(plate: String): VehicleResult<VehicleCount> =
        withSealed(plate) { request -> execute({ authorization -> api.link(authorization, request) }, ::toCount) }

    override suspend fun unlink(plate: String): VehicleResult<VehicleCount> =
        withSealed(plate) { request -> execute({ authorization -> api.unlink(authorization, request) }, ::toCount) }

    override suspend fun unlinkAll(): VehicleResult<Unit> =
        execute({ authorization -> api.unlinkAll(authorization) }) { VehicleResult.Success(Unit) }

    /** Cifra la patente y, si pudo, hace el pedido; si no, devuelve el fallo sin salir a la red. */
    private suspend fun <T> withSealed(
        plate: String,
        send: suspend (VehicleRequestDto) -> VehicleResult<T>,
    ): VehicleResult<T> {
        val sealed = withContext(dispatcher) {
            try {
                Sealed.Ok(VehicleRequestDto(payloadEnc = serializeAndEncrypt(plate)))
            } catch (e: CryptoConfigurationException) {
                Sealed.Err(VehicleError.Configuration)
            } catch (e: CryptoOperationException) {
                Sealed.Err(VehicleError.Unexpected)
            }
        }
        return when (sealed) {
            is Sealed.Ok -> send(sealed.request)
            is Sealed.Err -> VehicleResult.Failed(sealed.error)
        }
    }

    private sealed interface Sealed {
        data class Ok(val request: VehicleRequestDto) : Sealed

        data class Err(val error: VehicleError) : Sealed
    }

    private fun toCount(body: VehicleCountDto?): VehicleResult<VehicleCount> =
        // 2xx sin cuerpo: contrato roto, no se inventa un conteo.
        body?.let { VehicleResult.Success(VehicleCount(it.count, it.max)) }
            ?: VehicleResult.Failed(VehicleError.Unexpected)

    private suspend fun <B, T> execute(
        send: suspend (authorization: String) -> Response<B>,
        onSuccess: (B?) -> VehicleResult<T>,
    ): VehicleResult<T> = withContext(dispatcher) {
        // Detrás de una sesión activa esto no debería pasar (el NavHost ya redirige a Login sin
        // sesión), pero un fallo de lectura nunca debe traducirse en un crash.
        val accessToken = tokenStore.read()?.accessToken
            ?: return@withContext VehicleResult.Failed(VehicleError.Unexpected)

        try {
            val response = send(BEARER_PREFIX + accessToken)
            if (response.isSuccessful) onSuccess(response.body()) else VehicleResult.Failed(response.toError())
        } catch (e: CancellationException) {
            // El usuario abandonó la pantalla: cancelar no es un fallo. Va antes que
            // RuntimeException porque hereda de IllegalStateException.
            throw e
        } catch (e: CircuitBreakerOpenException) {
            VehicleResult.Failed(VehicleError.Unavailable)
        } catch (e: JsonEncodingException) {
            // La respuesta no es JSON: hereda de IOException, pero no es un problema de red.
            VehicleResult.Failed(VehicleError.Unexpected)
        } catch (e: IOException) {
            // Sin conexión, DNS, timeout, TLS.
            VehicleResult.Failed(VehicleError.Network)
        } catch (e: RuntimeException) {
            // Respuesta ilegible o cualquier fallo inesperado del stack HTTP.
            VehicleResult.Failed(VehicleError.Unexpected)
        }
    }

    /** Por el `code` del cuerpo; el 502/503/504 nativo de Kong no lo trae y se clasifica por status. */
    private fun Response<*>.toError(): VehicleError = when (readErrorCode()) {
        CODE_INVALID_PLATE -> VehicleError.InvalidPlate
        CODE_NOT_FOUND -> VehicleError.NotFound
        CODE_DUPLICATE -> VehicleError.Duplicate
        CODE_LIMIT_REACHED -> VehicleError.LimitReached
        CODE_DEPENDENCY_UNAVAILABLE -> VehicleError.Unavailable
        else -> if (code().isTransientUpstreamFailure()) VehicleError.Unavailable else VehicleError.Unexpected
    }

    private fun Response<*>.readErrorCode(): String? {
        val raw = try {
            errorBody()?.string()
        } catch (e: IOException) {
            null
        } ?: return null
        return try {
            errorAdapter.fromJson(raw)?.code
        } catch (e: Exception) {
            // Un cuerpo mal formado no debe tumbar el manejo del error en sí.
            null
        }
    }

    /**
     * Serializa la patente y la cifra sin que el JSON en claro llegue a existir como `String`: se
     * escribe directo a un buffer de bytes que se puede sobreescribir -- mismo criterio que
     * `SignUpRepositoryImpl.serializeAndEncrypt`.
     */
    private fun serializeAndEncrypt(plate: String): String {
        val buffer = Buffer()
        val plateBytes = plate.toByteArray(Charsets.UTF_8)
        var plaintext = ByteArray(0)
        return try {
            payloadAdapter.toJson(
                JsonWriter.of(buffer),
                VehiclePayloadDto(patenteBase64 = Base64.getEncoder().encodeToString(plateBytes)),
            )
            plaintext = buffer.readByteArray()
            encryptor.encrypt(plaintext, vehiclePublicKey).toCryptogram()
        } finally {
            plateBytes.fill(0)
            plaintext.fill(0)
            buffer.clear()
        }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "

        const val CODE_INVALID_PLATE = "INVALID_PLATE"
        const val CODE_NOT_FOUND = "VEHICLE_NOT_FOUND"
        const val CODE_DUPLICATE = "DUPLICATE_VEHICLE"
        const val CODE_LIMIT_REACHED = "VEHICLE_LIMIT_REACHED"
        const val CODE_DEPENDENCY_UNAVAILABLE = "DEPENDENCY_UNAVAILABLE"
    }
}
