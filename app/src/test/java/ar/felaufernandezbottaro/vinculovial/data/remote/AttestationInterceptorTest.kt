package ar.felaufernandezbottaro.vinculovial.data.remote

import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Cubre la excepción de los contratos US-03 y US-04: el header de attestation es
 * best-effort en `/logout` y `/vehicles` (nunca bloquea la request) pero sigue siendo
 * obligatorio en cualquier otro endpoint, incluido `/refresh`.
 */
class AttestationInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun clientWith(provider: AttestationProvider) = OkHttpClient.Builder()
        .addInterceptor(AttestationInterceptor(provider))
        .build()

    private fun requestTo(path: String) = Request.Builder()
        .url(server.url(path))
        .post("{}".toRequestBody("application/json".toMediaType()))
        .build()

    private fun failingProvider() = object : AttestationProvider {
        override suspend fun tokenFor(requestBody: ByteArray): String =
            throw AttestationUnavailableException("no disponible", AttestationUnavailableException.Reason.TRANSIENT)
    }

    @Test
    fun `si la attestation falla en logout la request sigue sin el header`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val response = clientWith(failingProvider()).newCall(requestTo("/api/v1/auth/logout")).execute()

        assertEquals(200, response.code)
        assertNull(server.takeRequest().getHeader(AttestationInterceptor.HEADER))
    }

    @Test(expected = IOException::class)
    fun `si la attestation falla en refresh la request no sale`() {
        clientWith(failingProvider()).newCall(requestTo("/api/v1/auth/refresh")).execute()
    }

    @Test
    fun `si la attestation falla en refresh no llega a pegarle al servidor`() {
        try {
            clientWith(failingProvider()).newCall(requestTo("/api/v1/auth/refresh")).execute()
        } catch (e: IOException) {
            // esperado -- ver el test anterior
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `si la attestation falla en vehicles la request sigue sin el header`() {
        server.enqueue(MockResponse().setResponseCode(201))

        val response = clientWith(failingProvider()).newCall(requestTo("/api/v1/vehicles")).execute()

        assertEquals(201, response.code)
        assertNull(server.takeRequest().getHeader(AttestationInterceptor.HEADER))
    }

    @Test
    fun `si la attestation funciona el header viaja igual en logout`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val provider = object : AttestationProvider {
            override suspend fun tokenFor(requestBody: ByteArray): String = "token-de-attestation"
        }

        clientWith(provider).newCall(requestTo("/api/v1/auth/logout")).execute()

        assertEquals("token-de-attestation", server.takeRequest().getHeader(AttestationInterceptor.HEADER))
    }
}
