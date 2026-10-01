package ar.felaufernandezbottaro.vinculovial.data.vehicle

import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoConfigurationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.crypto.toCryptogram
import ar.felaufernandezbottaro.vinculovial.data.remote.isTransientUpstreamFailure
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.LinkVehicleRequestDto
import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehiclePayloadDto
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.di.VehicleHpkePublicKey
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleResult
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleRepository
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaData
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Empaqueta los datos del vehículo en un único payload JSON, lo cifra contra la Public Key
 * de Vehicle y lo publica en el Gateway con el Access Token activo.
 *
 * Sin DNI ni Public Key de Identity acá: el backend resuelve el titular consultando una API
 * externa (RENAPER/DNRPA) a partir de chasis/patente/número de control.
 *
 * Traduce el mundo HTTP a errores de dominio: `presentation` nunca ve un código de estado
 * ni una excepción de red.
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

    override suspend fun linkVehicle(cedula: CedulaData): LinkVehicleResult = withContext(dispatcher) {
        // Detrás de una sesión activa esto no debería pasar (el NavHost ya redirige a Login
        // sin sesión) -- pero un fallo de lectura nunca debe traducirse en un crash.
        val accessToken = tokenStore.read()?.accessToken
            ?: return@withContext LinkVehicleResult.Failed(LinkVehicleError.Unexpected)

        val request = try {
            LinkVehicleRequestDto(payloadEnc = serializeAndEncrypt(cedula))
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
                response.code().isTransientUpstreamFailure() -> LinkVehicleResult.Failed(LinkVehicleError.Unavailable)
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

    /**
     * Serializa los datos del vehículo y los cifra sin que el JSON en claro llegue a
     * existir como `String`: se escribe directo a un buffer de bytes que se puede
     * sobreescribir -- mismo criterio que `SignUpRepositoryImpl.serializeAndEncrypt`.
     *
     * `chasis` y `patente` se codifican en base64 antes de entrar al JSON: el backend los
     * tipa `[]byte`, y `encoding/json` de Go decodifica esos campos como base64 siempre,
     * nunca como texto plano.
     */
    private fun serializeAndEncrypt(cedula: CedulaData): String {
        val buffer = Buffer()
        var plaintext = ByteArray(0)
        return try {
            payloadAdapter.toJson(
                JsonWriter.of(buffer),
                VehiclePayloadDto(
                    chasisBase64 = cedula.chasis.toByteArray(Charsets.UTF_8).toBase64(),
                    patenteBase64 = cedula.patente.toByteArray(Charsets.UTF_8).toBase64(),
                ),
            )
            plaintext = buffer.readByteArray()
            encryptor.encrypt(plaintext, vehiclePublicKey).toCryptogram()
        } finally {
            plaintext.fill(0)
            buffer.clear()
        }
    }

    /**
     * Base64 estándar sin salto de línea: el equivalente de `Base64.NO_WRAP` de Android.
     * `Base64.getEncoder()` (a diferencia de `getMimeEncoder()`) ya no inserta separadores
     * de línea y usa el alfabeto estándar (no URL-safe), que es lo que el backend en Go
     * espera con `base64.StdEncoding`.
     */
    private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_FORBIDDEN = 403
        const val HTTP_CONFLICT = 409
    }
}
