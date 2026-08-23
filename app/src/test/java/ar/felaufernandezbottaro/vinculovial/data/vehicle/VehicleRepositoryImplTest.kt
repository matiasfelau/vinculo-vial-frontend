package ar.felaufernandezbottaro.vinculovial.data.vehicle

import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleResult
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaData
import com.google.crypto.tink.Aead
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.Base64

/**
 * Cubre el contrato central de US-04 (revisado): los datos del vehículo se empaquetan en
 * un único JSON y viajan como un único `payload_enc` cifrado contra Vehicle -- sin DNI ni
 * Public Key de Identity acá -- y cada código de respuesta del Gateway mapea al resultado
 * de dominio correcto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleRepositoryImplTest {

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var api: FakeVehicleApi

    private val vehiclePrivateKeyset: KeysetHandle = KeysetHandle.generateNew(
        KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
    )
    private val vehiclePublicKeyBase64 = vehiclePrivateKeyset.publicKeyBase64()

    private val cedula = CedulaData(chasis = "APJ56565", patente = "GUQ856", nroControl = "5092160")

    @Before
    fun setUp() {
        HybridConfig.register()
        AeadConfig.register()
        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        tokenStore.save(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
        api = FakeVehicleApi()
    }

    private fun repository(vehiclePublicKey: String = vehiclePublicKeyBase64) = VehicleRepositoryImpl(
        api = api,
        encryptor = HpkeEncryptor(),
        vehiclePublicKey = vehiclePublicKey,
        tokenStore = tokenStore,
        moshi = Moshi.Builder().build(),
        dispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `el payload_enc solo lo puede abrir vehicle y trae chasis patente y nro_control`() = runTest {
        repository().linkVehicle(cedula)

        val payloadEnc = api.lastRequest!!.payloadEnc
        val json = JSONObject(String(decryptAsVehicle(payloadEnc)))

        assertEquals(cedula.chasis, json.getString("chasis"))
        assertEquals(cedula.patente, json.getString("patente"))
        assertEquals(cedula.nroControl, json.getString("nro_control"))
    }

    @Test
    fun `sin la clave privada de vehicle el payload no se puede abrir`() = runTest {
        val otraKeyset = KeysetHandle.generateNew(
            KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
        )

        repository().linkVehicle(cedula)

        val payloadEnc = api.lastRequest!!.payloadEnc
        assertThrows(Exception::class.java) {
            val sealed = Base64.getDecoder().decode(payloadEnc)
            otraKeyset.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java).decrypt(sealed, ByteArray(0))
        }
    }

    @Test
    fun `manda el access token como bearer`() = runTest {
        repository().linkVehicle(cedula)

        assertEquals("Bearer access-abc", api.lastAuthorization)
    }

    @Test
    fun `201 vincula`() = runTest {
        api.response = Response.success(Unit)

        val result = repository().linkVehicle(cedula)

        assertEquals(LinkVehicleResult.Linked, result)
    }

    @Test
    fun `400 es datos invalidos`() = runTest {
        api.response = errorResponse(400)

        val result = repository().linkVehicle(cedula)

        assertEquals(LinkVehicleResult.Failed(LinkVehicleError.InvalidData), result)
    }

    @Test
    fun `403 es mismatch de dueno`() = runTest {
        api.response = errorResponse(403)

        val result = repository().linkVehicle(cedula)

        assertEquals(LinkVehicleResult.Failed(LinkVehicleError.OwnerMismatch), result)
    }

    @Test
    fun `409 es ya vinculado`() = runTest {
        api.response = errorResponse(409)

        val result = repository().linkVehicle(cedula)

        assertEquals(LinkVehicleResult.Failed(LinkVehicleError.AlreadyLinked), result)
    }

    @Test
    fun `500 es un error inesperado`() = runTest {
        api.response = errorResponse(500)

        val result = repository().linkVehicle(cedula)

        assertEquals(LinkVehicleResult.Failed(LinkVehicleError.Unexpected), result)
    }

    @Test
    fun `un fallo de red es Network`() = runTest {
        api.exceptionToThrow = IOException("sin conexion")

        val result = repository().linkVehicle(cedula)

        assertEquals(LinkVehicleResult.Failed(LinkVehicleError.Network), result)
    }

    @Test
    fun `sin sesion guardada no llama a la red`() = runTest {
        tokenStore.clear()

        val result = repository().linkVehicle(cedula)

        assertEquals(LinkVehicleResult.Failed(LinkVehicleError.Unexpected), result)
        assertEquals(0, api.callCount)
    }

    @Test
    fun `clave de vehicle mal configurada es un error de configuracion`() = runTest {
        val result = repository(vehiclePublicKey = "").linkVehicle(cedula)

        assertEquals(LinkVehicleResult.Failed(LinkVehicleError.Configuration), result)
        assertEquals(0, api.callCount)
    }

    private fun errorResponse(code: Int) =
        Response.error<Unit>(code, "".toResponseBody("application/json".toMediaType()))

    private fun KeysetHandle.publicKeyBase64(): String = Base64.getEncoder().encodeToString(
        (publicKeysetHandle.primary.key as HpkePublicKey).publicKeyBytes.toByteArray(),
    )

    /** Reconstruye el mensaje HPKE a partir del cryptograma de un único campo y lo descifra. */
    private fun decryptAsVehicle(cryptogram: String): ByteArray {
        val sealed = Base64.getDecoder().decode(cryptogram)
        val decrypt = vehiclePrivateKeyset.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        return decrypt.decrypt(sealed, ByteArray(0))
    }
}
