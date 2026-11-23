package ar.felaufernandezbottaro.vinculovial.data.remote.auth

import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationTokenGenerator
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.crypto.MockAttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import ar.felaufernandezbottaro.vinculovial.data.remote.AuthApiService
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Base64

/**
 * Gateway simulado de los endpoints públicos de `/auth` (US-13): un `MockWebServer`, el
 * interceptor de attestation real y una clave HPKE de Identity de prueba, para verificar el
 * contrato de cada pedido y que Identity pueda descifrar lo que la app le manda.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthWireHarness {

    lateinit var server: MockWebServer
        private set
    lateinit var api: AuthApiService
        private set

    private lateinit var identityKeyset: KeysetHandle
    lateinit var identityPublicKeyBase64: String
        private set

    /** Por defecto el modo mock; los tests que necesitan fallar lo reemplazan. */
    var attestationProvider: AttestationProvider = MockAttestationProvider(
        AttestationTokenGenerator(
            psk = "psk-de-desarrollo-de-al-menos-32-bytes",
            ttlSeconds = 60,
            packageName = "ar.felaufernandezbottaro.vinculovial",
        ),
    )

    /** Simula el circuito abierto sin instanciar el `CircuitBreakerInterceptor` real. */
    var forceCircuitOpen = false

    fun start() {
        HybridConfig.register()
        identityKeyset = KeysetHandle.generateNew(
            KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"),
        )
        identityPublicKeyBase64 = Base64.getEncoder().encodeToString(
            (identityKeyset.publicKeysetHandle.primary.key as HpkePublicKey).publicKeyBytes.toByteArray(),
        )

        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                if (forceCircuitOpen) throw CircuitBreakerOpenException("auth") else chain.proceed(chain.request())
            }
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

    fun stop() {
        server.shutdown()
    }

    fun TestScope.caller(publicKey: String = identityPublicKeyBase64) = SealedAuthCaller(
        encryptor = HpkeEncryptor(),
        identityPublicKey = publicKey,
        moshi = Moshi.Builder().build(),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    /** El cuerpo del pedido: el sobre HPKE tal como viaja por HTTP. */
    fun envelopeOf(request: RecordedRequest): JSONObject = JSONObject(request.body.readUtf8())

    /** Lo que Identity ve al descifrar el sobre, con `info` vacío como en el signup. */
    fun decrypt(request: RecordedRequest): JSONObject {
        val body = envelopeOf(request)
        val enc = Base64.getDecoder().decode(body.getString("ephemeral_public_key"))
        val ciphertext = Base64.getDecoder().decode(body.getString("ciphertext"))
        val plaintext = identityKeyset
            .getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
            .decrypt(enc + ciphertext, ByteArray(0))
        return JSONObject(String(plaintext))
    }

    fun JSONObject.keySet(): Set<String> = keys().asSequence().toSet()
}
