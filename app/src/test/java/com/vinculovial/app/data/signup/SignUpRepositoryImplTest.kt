package com.vinculovial.app.data.signup

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import com.squareup.moshi.Moshi
import com.vinculovial.app.data.crypto.AttestationProvider
import com.vinculovial.app.data.crypto.AttestationTokenGenerator
import com.vinculovial.app.data.crypto.AttestationUnavailableException
import com.vinculovial.app.data.crypto.HpkeEncryptor
import com.vinculovial.app.data.crypto.MockAttestationProvider
import com.vinculovial.app.data.remote.AttestationInterceptor
import com.vinculovial.app.data.remote.AuthApiService
import com.vinculovial.app.data.signup.dto.SignUpRequestDto
import com.vinculovial.app.domain.signup.SignUpError
import com.vinculovial.app.domain.signup.SignUpResult
import com.vinculovial.app.domain.signup.model.Credentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Base64

/**
 * Test de contrato contra el Gateway simulado: verifica el endpoint, el header de
 * attestation, la forma del payload externo y que Identity pueda descifrar el interno.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignUpRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AuthApiService
    private lateinit var identityKeyset: KeysetHandle
    private lateinit var identityPublicKeyBase64: String

    private val credentials = Credentials(
        dni = "12345678",
        email = "usuario@ejemplo.com",
        password = "Password123!",
        biometricVerified = true,
    )

    /** Por defecto el modo mock; los tests que necesitan fallar lo reemplazan. */
    private var attestationProvider: AttestationProvider = MockAttestationProvider(
        AttestationTokenGenerator(
            psk = "psk-de-desarrollo-de-al-menos-32-bytes",
            ttlSeconds = 60,
            packageName = "com.vinculovial.app",
        ),
    )

    @Before
    fun setUp() {
        HybridConfig.register()
        identityKeyset = KeysetHandle.generateNew(
            KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
        )
        identityPublicKeyBase64 = Base64.getEncoder().encodeToString(
            (identityKeyset.publicKeysetHandle.primary.key as HpkePublicKey).publicKeyBytes.toByteArray(),
        )

        server = MockWebServer()
        server.start()

        // Delega en el campo para que un test pueda sustituir el proveedor después
        // de que el cliente ya esté construido.
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

    private fun TestScope.repository(publicKey: String = identityPublicKeyBase64) = SignUpRepositoryImpl(
        api = api,
        encryptor = HpkeEncryptor(publicKey),
        moshi = Moshi.Builder().build(),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    @Test
    fun `201 registra al usuario`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = repository().signUp(credentials)

        assertEquals(SignUpResult.Registered, result)
    }

    @Test
    fun `publica en el endpoint y con el header del contrato`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        repository().signUp(credentials)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/signup", request.path)
        assertNotNull(
            "falta el header de attestation",
            request.getHeader(AttestationInterceptor.HEADER),
        )
    }

    @Test
    fun `el cuerpo es el payload externo hpke`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        repository().signUp(credentials)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(body.has("ephemeral_public_key"))
        assertTrue(body.has("ciphertext"))
        assertEquals(2, body.length())
        assertEquals(32, Base64.getDecoder().decode(body.getString("ephemeral_public_key")).size)
    }

    @Test
    fun `identity puede descifrar el payload interno`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        repository().signUp(credentials)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        val enc = Base64.getDecoder().decode(body.getString("ephemeral_public_key"))
        val ciphertext = Base64.getDecoder().decode(body.getString("ciphertext"))
        val plaintext = identityKeyset
            .getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
            .decrypt(enc + ciphertext, ByteArray(0))

        val payload = JSONObject(String(plaintext))
        assertEquals(credentials.dni, payload.getString("dni"))
        assertEquals(credentials.email, payload.getString("email"))
        assertEquals(credentials.password, payload.getString("password"))
        assertTrue(payload.getBoolean("biometric_verified"))
    }

    @Test
    fun `el payload cifrado no lleva datos biometricos`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        repository().signUp(credentials)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        val enc = Base64.getDecoder().decode(body.getString("ephemeral_public_key"))
        val ciphertext = Base64.getDecoder().decode(body.getString("ciphertext"))
        val plaintext = identityKeyset
            .getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
            .decrypt(enc + ciphertext, ByteArray(0))

        // De toda la verificación biométrica sólo puede viajar el booleano: ni imágenes,
        // ni embeddings, ni el puntaje de similitud.
        val payload = JSONObject(String(plaintext))
        assertEquals(
            setOf("dni", "email", "password", "biometric_verified"),
            payload.keys().asSequence().toSet(),
        )
    }

    @Test
    fun `el cuerpo no viaja en claro`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        repository().signUp(credentials)

        val raw = server.takeRequest().body.readUtf8()
        listOf(credentials.dni, credentials.email, credentials.password).forEach {
            assertTrue("el cuerpo filtró '$it'", !raw.contains(it))
        }
    }

    @Test
    fun `400 es dato invalido`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))

        assertEquals(SignUpResult.Failed(SignUpError.InvalidData), repository().signUp(credentials))
    }

    @Test
    fun `403 es attestation rechazada`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        assertEquals(
            SignUpResult.Failed(SignUpError.AttestationRejected),
            repository().signUp(credentials),
        )
    }

    @Test
    fun `409 es cuenta ya registrada`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409))

        assertEquals(
            SignUpResult.Failed(SignUpError.AlreadyRegistered),
            repository().signUp(credentials),
        )
    }

    @Test
    fun `500 cae en inesperado`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(SignUpResult.Failed(SignUpError.Unexpected), repository().signUp(credentials))
    }

    @Test
    fun `servidor caido es error de red`() = runTest {
        server.shutdown()

        assertEquals(SignUpResult.Failed(SignUpError.Network), repository().signUp(credentials))
    }

    @Test
    fun `sin clave de identity no se hace la request`() = runTest {
        val result = repository(publicKey = "").signUp(credentials)

        assertEquals(SignUpResult.Failed(SignUpError.Configuration), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `el token de attestation se liga al cuerpo enviado`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        repository().signUp(credentials)

        val request = server.takeRequest()
        val enviado = request.body.readByteArray()
        // El Gateway espera el hash en hex, no en base64: si el formato no calza acá,
        // el 403 de attestation nunca se explica solo.
        val esperado = java.security.MessageDigest.getInstance("SHA-256")
            .digest(enviado)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

        val claims = String(
            Base64.getUrlDecoder().decode(
                request.getHeader(AttestationInterceptor.HEADER)!!.split(".")[1],
            ),
        )
        assertTrue("el token no está ligado al cuerpo", claims.contains(esperado))
        assertTrue("el hash no está en hexadecimal", esperado.matches(Regex("^[0-9a-f]{64}$")))
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

        val result = repository().signUp(credentials)

        assertEquals(SignUpResult.Failed(SignUpError.DeviceNotTrusted), result)
        assertEquals("no debió salir a la red", 0, server.requestCount)
    }

    @Test
    fun `sin play store el error invita a reintentar`() = runTest {
        attestationProvider = failingProvider(AttestationUnavailableException.Reason.PLAY_UNAVAILABLE)

        assertEquals(
            SignUpResult.Failed(SignUpError.AttestationUnavailable),
            repository().signUp(credentials),
        )
    }

    @Test
    fun `attestation mal configurada es error de configuracion`() = runTest {
        attestationProvider = failingProvider(AttestationUnavailableException.Reason.MISCONFIGURED)

        assertEquals(
            SignUpResult.Failed(SignUpError.Configuration),
            repository().signUp(credentials),
        )
    }

    @Test
    fun `cancelar la request no se convierte en un error de registro`() = runTest {
        // CancellationException hereda de IllegalStateException y por lo tanto de
        // RuntimeException: si se la captura junto al resto, abandonar la pantalla
        // termina mostrando "algo salió mal" en vez de cancelar en silencio.
        val cancelada = object : AuthApiService {
            override suspend fun signUp(body: SignUpRequestDto): Response<Unit> =
                throw CancellationException("pantalla abandonada")
        }
        val repository = SignUpRepositoryImpl(
            api = cancelada,
            encryptor = HpkeEncryptor(identityPublicKeyBase64),
            moshi = Moshi.Builder().build(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { repository.signUp(credentials) }
        }
    }
}
