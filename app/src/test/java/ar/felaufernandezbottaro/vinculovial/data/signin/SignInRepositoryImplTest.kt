package ar.felaufernandezbottaro.vinculovial.data.signin

import com.google.crypto.tink.Aead
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import com.squareup.moshi.Moshi
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationTokenGenerator
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.crypto.MockAttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import ar.felaufernandezbottaro.vinculovial.data.remote.AuthApiService
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInError
import ar.felaufernandezbottaro.vinculovial.domain.signin.SignInResult
import ar.felaufernandezbottaro.vinculovial.domain.signin.model.SignInCredentials
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Base64

/**
 * Test de contrato contra el Gateway simulado: verifica el endpoint, el header de
 * attestation, la forma del payload externo, que Identity pueda descifrar el interno, y
 * que el 200 deje el par de tokens persistido en `SecureTokenStore`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AuthApiService
    private lateinit var identityKeyset: KeysetHandle
    private lateinit var identityPublicKeyBase64: String
    private lateinit var tokenStore: SecureTokenStore

    private val credentials = SignInCredentials(dni = "12345678", password = "Password123!")

    /** Por defecto el modo mock; los tests que necesitan fallar lo reemplazan. */
    private var attestationProvider: AttestationProvider = MockAttestationProvider(
        AttestationTokenGenerator(
            psk = "psk-de-desarrollo-de-al-menos-32-bytes",
            ttlSeconds = 60,
            packageName = "ar.felaufernandezbottaro.vinculovial",
        ),
    )

    @Before
    fun setUp() {
        HybridConfig.register()
        AeadConfig.register()
        identityKeyset = KeysetHandle.generateNew(
            KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
        )
        identityPublicKeyBase64 = Base64.getEncoder().encodeToString(
            (identityKeyset.publicKeysetHandle.primary.key as HpkePublicKey).publicKeyBytes.toByteArray(),
        )

        val sessionAead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), sessionAead)

        server = MockWebServer()
        server.start()

        // Delega en el campo para que un test pueda sustituir el proveedor después de que
        // el cliente ya esté construido.
        val client = OkHttpClient.Builder()
            .addInterceptor(
                AttestationInterceptor(
                    object : AttestationProvider {
                        override suspend fun tokenFor(requestBody: ByteArray): String =
                            attestationProvider.tokenFor(requestBody)
                    },
                ),
            )
            .build()

        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(AuthApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun TestScope.repository(publicKey: String = identityPublicKeyBase64) = SignInRepositoryImpl(
        api = api,
        encryptor = HpkeEncryptor(),
        identityPublicKey = publicKey,
        tokenStore = tokenStore,
        moshi = Moshi.Builder().build(),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private fun tokenResponse(access: String = "access-abc", refresh: String = "refresh-xyz") =
        MockResponse().setResponseCode(200).setBody(
            """{"access_token":"$access","refresh_token":"$refresh"}""",
        )

    @Test
    fun `200 inicia sesion`() = runTest {
        server.enqueue(tokenResponse())

        val result = repository().signIn(credentials)

        assertEquals(SignInResult.SignedIn, result)
    }

    @Test
    fun `200 deja el par de tokens persistido`() = runTest {
        server.enqueue(tokenResponse(access = "access-abc", refresh = "refresh-xyz"))

        repository().signIn(credentials)

        val stored = tokenStore.read()
        assertEquals("access-abc", stored?.accessToken)
        assertEquals("refresh-xyz", stored?.refreshToken)
    }

    @Test
    fun `publica en el endpoint y con el header del contrato`() = runTest {
        server.enqueue(tokenResponse())

        repository().signIn(credentials)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/signin", request.path)
        assertNotNull(
            "falta el header de attestation",
            request.getHeader(AttestationInterceptor.HEADER),
        )
    }

    @Test
    fun `el cuerpo es el payload externo hpke`() = runTest {
        server.enqueue(tokenResponse())

        repository().signIn(credentials)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(body.has("ephemeral_public_key"))
        assertTrue(body.has("ciphertext"))
        assertEquals(2, body.length())
        assertEquals(32, Base64.getDecoder().decode(body.getString("ephemeral_public_key")).size)
    }

    @Test
    fun `identity puede descifrar el payload interno`() = runTest {
        server.enqueue(tokenResponse())

        repository().signIn(credentials)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        val enc = Base64.getDecoder().decode(body.getString("ephemeral_public_key"))
        val ciphertext = Base64.getDecoder().decode(body.getString("ciphertext"))
        val plaintext = identityKeyset
            .getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
            .decrypt(enc + ciphertext, ByteArray(0))

        val payload = JSONObject(String(plaintext))
        assertEquals(credentials.dni, payload.getString("dni"))
        assertEquals(credentials.password, payload.getString("password"))
        assertEquals(setOf("dni", "password"), payload.keys().asSequence().toSet())
    }

    @Test
    fun `el cuerpo no viaja en claro`() = runTest {
        server.enqueue(tokenResponse())

        repository().signIn(credentials)

        val raw = server.takeRequest().body.readUtf8()
        listOf(credentials.dni, credentials.password).forEach {
            assertTrue("el cuerpo filtró '$it'", !raw.contains(it))
        }
    }

    @Test
    fun `400 es dato invalido`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))

        assertEquals(SignInResult.Failed(SignInError.InvalidData), repository().signIn(credentials))
    }

    @Test
    fun `401 es credenciales invalidas`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(SignInResult.Failed(SignInError.InvalidCredentials), repository().signIn(credentials))
    }

    @Test
    fun `un 401 no persiste ningun token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        repository().signIn(credentials)

        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `403 es attestation rechazada`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        assertEquals(SignInResult.Failed(SignInError.AttestationRejected), repository().signIn(credentials))
    }

    @Test
    fun `500 cae en inesperado`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(SignInResult.Failed(SignInError.Unexpected), repository().signIn(credentials))
    }

    @Test
    fun `200 con cuerpo no parseable no persiste nada`() = runTest {
        // Moshi tira una excepción de lectura ni bien encuentra un JSON inválido — no llega
        // a existir un Response con body() == null para distinguir "sin cuerpo" de "cuerpo
        // roto". Esa excepción es un IOException, así que cae en el mismo balde que
        // cualquier otro fallo del transporte: no debería pasar nunca si el backend respeta
        // el contrato, y lo importante acá es que un 200 mal formado jamás deja tokens a
        // medio guardar.
        server.enqueue(MockResponse().setResponseCode(200).setBody("no es json"))

        val result = repository().signIn(credentials)

        assertEquals(SignInResult.Failed(SignInError.Network), result)
        assertEquals(null, tokenStore.read())
    }

    @Test
    fun `servidor caido es error de red`() = runTest {
        server.shutdown()

        assertEquals(SignInResult.Failed(SignInError.Network), repository().signIn(credentials))
    }

    @Test
    fun `sin clave de identity no se hace la request`() = runTest {
        val result = repository(publicKey = "").signIn(credentials)

        assertEquals(SignInResult.Failed(SignInError.Configuration), result)
        assertEquals(0, server.requestCount)
    }

    /** Proveedor que siempre falla, para cubrir el mapeo de causas a errores de dominio. */
    private fun failingProvider(reason: AttestationUnavailableException.Reason) =
        object : AttestationProvider {
            override suspend fun tokenFor(requestBody: ByteArray): String =
                throw AttestationUnavailableException("attestation falló", reason)
        }

    @Test
    fun `un dispositivo no confiable no se confunde con un fallo de red`() = runTest {
        attestationProvider = failingProvider(AttestationUnavailableException.Reason.DEVICE_NOT_TRUSTED)

        val result = repository().signIn(credentials)

        assertEquals(SignInResult.Failed(SignInError.DeviceNotTrusted), result)
        assertEquals("no debió salir a la red", 0, server.requestCount)
    }

    @Test
    fun `sin play store el error invita a reintentar`() = runTest {
        attestationProvider = failingProvider(AttestationUnavailableException.Reason.PLAY_UNAVAILABLE)

        assertEquals(
            SignInResult.Failed(SignInError.AttestationUnavailable),
            repository().signIn(credentials),
        )
    }

    @Test
    fun `attestation mal configurada es error de configuracion`() = runTest {
        attestationProvider = failingProvider(AttestationUnavailableException.Reason.MISCONFIGURED)

        assertEquals(
            SignInResult.Failed(SignInError.Configuration),
            repository().signIn(credentials),
        )
    }
}
