package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerConfig
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerInterceptor
import ar.felaufernandezbottaro.vinculovial.data.remote.NetworkTimeouts
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
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifica lo que `NetworkModule` le configura a los dos `OkHttpClient`, no constantes: cada
 * assert lee el cliente construido.
 *
 * Los timeouts entran por [NetworkTimeouts] con valores **centinela** -- distintos entre sí y
 * de los defaults de OkHttp (10 s de connect/read/write, 0 = sin límite de `callTimeout`).
 * Si el test usara el `BuildConfig` real, un build configurado con 10000 coincidiría con el
 * default y el test pasaría igual aunque se borraran las líneas de timeout del módulo.
 */
class NetworkModuleTest {

    private val sentinel = NetworkTimeouts(
        connectMs = 12_001,
        readMs = 13_002,
        writeMs = 14_003,
        attestationMs = 7_004,
    )

    private val breaker = CircuitBreakerInterceptor(CircuitBreakerConfig(3, 60_000, 30_000))
    private val attestation = AttestationInterceptor(
        object : AttestationProvider {
            override suspend fun tokenFor(requestBody: ByteArray) = "token"
        },
    )
    private val logging = HttpLoggingInterceptor()
    private lateinit var tokenAuthenticator: TokenAuthenticator

    @Before
    fun setUp() {
        AeadConfig.register()
        val aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        val store = SecureTokenStore(FakeSharedPreferences(), aead)
        tokenAuthenticator = TokenAuthenticator(SessionManager(FakeRefreshApi(), store, Dispatchers.Unconfined))
    }

    private fun authenticatedClient() =
        NetworkModule.provideOkHttpClient(sentinel, breaker, attestation, logging, tokenAuthenticator)

    private fun refreshClient() =
        NetworkModule.provideRefreshOkHttpClient(sentinel, breaker, attestation, logging)

    private fun clients(): Map<String, OkHttpClient> =
        mapOf("autenticado" to authenticatedClient(), "refresh" to refreshClient())

    @Test
    fun `los centinelas no coinciden con ningun default de OkHttp`() {
        val defaults = OkHttpClient()

        assertNotEquals(defaults.connectTimeoutMillis.toLong(), sentinel.connectMs)
        assertNotEquals(defaults.readTimeoutMillis.toLong(), sentinel.readMs)
        assertNotEquals(defaults.writeTimeoutMillis.toLong(), sentinel.writeMs)
        assertEquals("el default de OkHttp es sin límite", 0, defaults.callTimeoutMillis)
    }

    @Test
    fun `los dos clientes aplican connect, read y write de NetworkTimeouts`() {
        clients().forEach { (name, client) ->
            assertEquals("connect del cliente $name", sentinel.connectMs, client.connectTimeoutMillis.toLong())
            assertEquals("read del cliente $name", sentinel.readMs, client.readTimeoutMillis.toLong())
            assertEquals("write del cliente $name", sentinel.writeMs, client.writeTimeoutMillis.toLong())
        }
    }

    /** El default de OkHttp es 0 (sin límite): un callTimeout borrado se ve acá como 0. */
    @Test
    fun `los dos clientes tienen un callTimeout finito que cubre attestation + connect + read`() {
        clients().forEach { (name, client) ->
            val call = client.callTimeoutMillis.toLong()
            val worstCase = sentinel.attestationMs + client.connectTimeoutMillis + client.readTimeoutMillis

            assertTrue("callTimeout del cliente $name no puede ser 0 (sin límite)", call > 0)
            assertEquals("callTimeout del cliente $name", sentinel.callMs, call)
            assertTrue("callTimeout ($call) debe superar attestation + connect + read ($worstCase) en $name", call > worstCase)
        }
    }

    @Test
    fun `ningun cliente reintenta por su cuenta`() {
        clients().forEach { (name, client) ->
            assertFalse("retryOnConnectionFailure del cliente $name", client.retryOnConnectionFailure)
        }
    }

    @Test
    fun `el breaker va primero y antes que attestation, y es la misma instancia en los dos clientes`() {
        clients().forEach { (name, client) ->
            val interceptors = client.interceptors
            val breakerAt = interceptors.indexOfFirst { it is CircuitBreakerInterceptor }
            val attestationAt = interceptors.indexOfFirst { it is AttestationInterceptor }

            assertEquals("el breaker tiene que ser el primer interceptor del cliente $name", 0, breakerAt)
            assertTrue("attestation tiene que ir después del breaker en $name", attestationAt > breakerAt)
            assertSame("estado compartido entre clientes ($name)", breaker, interceptors[breakerAt])
        }
    }

    @Test
    fun `solo el cliente autenticado lleva TokenAuthenticator`() {
        assertSame(tokenAuthenticator, authenticatedClient().authenticator)
        assertSame(
            "un 401 de /refresh o /logout no puede disparar otro refresh",
            Authenticator.NONE,
            refreshClient().authenticator,
        )
    }

    /** La configuración real de producción, no los centinelas. Sólo cota lo que dice el contrato. */
    @Test
    fun `la configuracion de produccion respeta el contrato de timeouts`() {
        val production = NetworkModule.provideNetworkTimeouts()

        assertTrue("read_timeout >= 15000 (contrato), es ${production.readMs}", production.readMs >= 15_000)
        assertTrue(production.connectMs > 0 && production.writeMs > 0 && production.attestationMs > 0)
        assertTrue(production.callMs > production.attestationMs + production.connectMs + production.readMs)
    }
}
