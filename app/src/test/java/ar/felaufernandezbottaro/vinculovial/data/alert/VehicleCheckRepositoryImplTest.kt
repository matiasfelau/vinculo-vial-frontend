package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.scan.CheckVehicleError
import ar.felaufernandezbottaro.vinculovial.domain.scan.CheckVehicleResult
import com.google.crypto.tink.Aead
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.Base64

/**
 * Cubre el flujo de dos pasos de US-05: la patente se cifra directa (sin envoltorio JSON,
 * a diferencia del `payload_enc` de US-04) contra Vehicle, y cada código de respuesta del
 * Gateway mapea al resultado de dominio correcto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleCheckRepositoryImplTest {

    companion object {
        // @BeforeClass, no @Before: los keyset de acá abajo son field initializers, que
        // corren en el constructor -- antes de cualquier @Before -- así que Tink tiene que
        // quedar registrado antes de que exista la primera instancia de la clase.
        @BeforeClass
        @JvmStatic
        fun registerTink() {
            HybridConfig.register()
            AeadConfig.register()
        }
    }

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var api: FakeVehicleCheckApi

    private val vehiclePrivateKeyset: KeysetHandle = KeysetHandle.generateNew(
        KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
    )
    private val vehiclePublicKeyBase64 = vehiclePrivateKeyset.publicKeyBase64()

    private val patente = "GUQ856"

    @Before
    fun setUp() {
        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        tokenStore.save(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
        api = FakeVehicleCheckApi()
    }

    private fun repository(vehiclePublicKey: String = vehiclePublicKeyBase64) = VehicleCheckRepositoryImpl(
        api = api,
        encryptor = HpkeEncryptor(),
        vehiclePublicKey = vehiclePublicKey,
        tokenStore = tokenStore,
        dispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `el pat_enc solo lo puede abrir vehicle y contiene la patente en claro`() = runTest {
        repository().checkVehicle(patente)

        val patEnc = api.lastRequest!!.patEnc
        assertEquals(patente, String(decryptAsVehicle(patEnc)))
    }

    @Test
    fun `manda el access token como bearer`() = runTest {
        repository().checkVehicle(patente)

        assertEquals("Bearer access-abc", api.lastAuthorization)
    }

    @Test
    fun `200 devuelve Found con el mismo pat_enc que se envio`() = runTest {
        api.response = Response.success(Unit)

        val result = repository().checkVehicle(patente)

        assertTrue(result is CheckVehicleResult.Found)
        assertEquals(api.lastRequest!!.patEnc, (result as CheckVehicleResult.Found).patEnc)
    }

    @Test
    fun `404 es NotInNetwork`() = runTest {
        api.response = errorResponse(404)

        val result = repository().checkVehicle(patente)

        assertEquals(CheckVehicleResult.NotInNetwork, result)
    }

    @Test
    fun `429 es RateLimited`() = runTest {
        api.response = errorResponse(429)

        val result = repository().checkVehicle(patente)

        assertEquals(CheckVehicleResult.Failed(CheckVehicleError.RateLimited), result)
    }

    @Test
    fun `500 es un error inesperado`() = runTest {
        api.response = errorResponse(500)

        val result = repository().checkVehicle(patente)

        assertEquals(CheckVehicleResult.Failed(CheckVehicleError.Unexpected), result)
    }

    @Test
    fun `un fallo de red es Network`() = runTest {
        api.exceptionToThrow = IOException("sin conexion")

        val result = repository().checkVehicle(patente)

        assertEquals(CheckVehicleResult.Failed(CheckVehicleError.Network), result)
    }

    @Test
    fun `sin sesion guardada no llama a la red`() = runTest {
        tokenStore.clear()

        val result = repository().checkVehicle(patente)

        assertEquals(CheckVehicleResult.Failed(CheckVehicleError.Unexpected), result)
        assertEquals(0, api.callCount)
    }

    @Test
    fun `clave de vehicle mal configurada es un error de configuracion`() = runTest {
        val result = repository(vehiclePublicKey = "").checkVehicle(patente)

        assertEquals(CheckVehicleResult.Failed(CheckVehicleError.Configuration), result)
        assertEquals(0, api.callCount)
    }

    private fun errorResponse(code: Int) =
        Response.error<Unit>(code, "".toResponseBody("application/json".toMediaType()))

    private fun KeysetHandle.publicKeyBase64(): String = Base64.getEncoder().encodeToString(
        (publicKeysetHandle.primary.key as HpkePublicKey).publicKeyBytes.toByteArray(),
    )

    private fun decryptAsVehicle(cryptogram: String): ByteArray {
        val sealed = Base64.getDecoder().decode(cryptogram)
        val decrypt = vehiclePrivateKeyset.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        return decrypt.decrypt(sealed, ByteArray(0))
    }
}
