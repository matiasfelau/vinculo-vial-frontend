package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.BuildConfig
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerConfig
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerInterceptor
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.TokenAuthenticator
import ar.felaufernandezbottaro.vinculovial.data.session.FakeRefreshApi
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.Dispatchers
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkModuleTest {

    private lateinit var circuitBreakerInterceptor: CircuitBreakerInterceptor
    private lateinit var attestationInterceptor: AttestationInterceptor
    private lateinit var loggingInterceptor: HttpLoggingInterceptor
    private lateinit var tokenAuthenticator: TokenAuthenticator

    @Before
    fun setUp() {
        circuitBreakerInterceptor = CircuitBreakerInterceptor(CircuitBreakerConfig(1, 1000, 1000))
        
        attestationInterceptor = AttestationInterceptor(
            object : AttestationProvider {
                override suspend fun tokenFor(requestBody: ByteArray) = "dummy"
            }
        )
        
        loggingInterceptor = HttpLoggingInterceptor()

        AeadConfig.register()
        val aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        
        val store = SecureTokenStore(FakeSharedPreferences(), aead)
        val sessionManager = SessionManager(FakeRefreshApi(), store, Dispatchers.Unconfined)
        tokenAuthenticator = TokenAuthenticator(sessionManager)
    }

    @Test
    fun `provideOkHttpClient configura los timeouts y cumple la invariante de callTimeout`() {
        val client = NetworkModule.provideOkHttpClient(
            circuitBreakerInterceptor,
            attestationInterceptor,
            loggingInterceptor,
            tokenAuthenticator
        )

        assertEquals(BuildConfig.NETWORK_TIMEOUT_MS.toLong(), client.connectTimeoutMillis.toLong())
        assertEquals(BuildConfig.NETWORK_TIMEOUT_MS.toLong(), client.readTimeoutMillis.toLong())
        assertEquals(BuildConfig.NETWORK_TIMEOUT_MS.toLong(), client.writeTimeoutMillis.toLong())

        val callTimeout = client.callTimeoutMillis.toLong()
        val minRequired = BuildConfig.ATTESTATION_TIMEOUT_MS.toLong() + client.connectTimeoutMillis + client.readTimeoutMillis

        assertTrue(
            "CALL_TIMEOUT_MS (\$callTimeout) debe ser estrictamente mayor a ATTESTATION_TIMEOUT_MS + connect + read (\$minRequired)",
            callTimeout > minRequired
        )
    }

    @Test
    fun `provideRefreshOkHttpClient configura los timeouts y cumple la invariante de callTimeout`() {
        val client = NetworkModule.provideRefreshOkHttpClient(
            circuitBreakerInterceptor,
            attestationInterceptor,
            loggingInterceptor
        )

        assertEquals(BuildConfig.NETWORK_TIMEOUT_MS.toLong(), client.connectTimeoutMillis.toLong())
        assertEquals(BuildConfig.NETWORK_TIMEOUT_MS.toLong(), client.readTimeoutMillis.toLong())
        assertEquals(BuildConfig.NETWORK_TIMEOUT_MS.toLong(), client.writeTimeoutMillis.toLong())

        val callTimeout = client.callTimeoutMillis.toLong()
        val minRequired = BuildConfig.ATTESTATION_TIMEOUT_MS.toLong() + client.connectTimeoutMillis + client.readTimeoutMillis

        assertTrue(
            "CALL_TIMEOUT_MS (\$callTimeout) debe ser estrictamente mayor a ATTESTATION_TIMEOUT_MS + connect + read (\$minRequired)",
            callTimeout > minRequired
        )
    }
}
