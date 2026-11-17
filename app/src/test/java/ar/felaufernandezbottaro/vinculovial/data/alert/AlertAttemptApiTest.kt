package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.AlertAttemptRequestDto
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationTokenGenerator
import ar.felaufernandezbottaro.vinculovial.data.crypto.MockAttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Test de contrato contra el Gateway simulado para `/alerts/attempts`: sólo la forma de la
 * request (el `pat_enc` en el body, US-16) y de la respuesta 201. El mapeo de códigos de respuesta a resultado de
 * dominio vive en `AlertAttemptRepositoryImpl`, no acá.
 */
class AlertAttemptApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AlertAttemptApi

    @Before
    fun setUp() {
        val attestationProvider: AttestationProvider = MockAttestationProvider(
            AttestationTokenGenerator(
                psk = "psk-de-desarrollo-de-al-menos-32-bytes",
                ttlSeconds = 60,
                packageName = "ar.felaufernandezbottaro.vinculovial",
            ),
        )

        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder()
            .addInterceptor(AttestationInterceptor(attestationProvider))
            .build()

        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(AlertAttemptApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `publica en el endpoint del contrato con el bearer token`() = runTest {
        server.enqueue(createdResponse())

        api.startAttempt("Bearer access-abc", AlertAttemptRequestDto(patEnc = "pat-enc"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/alerts/attempts", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
    }

    @Test
    fun `el body es un JSON con el pat_enc y nada mas`() = runTest {
        server.enqueue(createdResponse())

        api.startAttempt("Bearer access-abc", AlertAttemptRequestDto(patEnc = "cGF0LWVuYw=="))

        val request = server.takeRequest()
        assertEquals("""{"pat_enc":"cGF0LWVuYw=="}""", request.body.readUtf8())
        assertEquals(true, request.getHeader("Content-Type").orEmpty().startsWith("application/json"))
    }

    @Test
    fun `el body del intento es chico, muy por debajo del limite de 4 KB del contrato`() = runTest {
        server.enqueue(createdResponse())

        // Un pat_enc real (enc de 32 bytes + patente + tag) en base64 mide menos de 100 caracteres.
        api.startAttempt("Bearer access-abc", AlertAttemptRequestDto(patEnc = "A".repeat(100)))

        assertEquals(true, server.takeRequest().bodySize < 4 * 1024)
    }

    @Test
    fun `el toString del body no imprime el pat_enc`() {
        assertEquals("AlertAttemptRequestDto(patEnc=***)", AlertAttemptRequestDto(patEnc = "secreto").toString())
    }

    @Test
    fun `201 trae attempt_id y expires_at`() = runTest {
        server.enqueue(createdResponse())

        val response = api.startAttempt("Bearer access-abc", AlertAttemptRequestDto(patEnc = "pat-enc"))

        assertEquals(201, response.code())
        assertEquals("3f2b8c1e-0000-4000-8000-000000000001", response.body()?.attemptId)
        assertEquals("2030-01-01T00:10:00Z", response.body()?.expiresAt)
    }

    private fun createdResponse() = MockResponse()
        .setResponseCode(201)
        .setBody("""{"attempt_id":"3f2b8c1e-0000-4000-8000-000000000001","expires_at":"2030-01-01T00:10:00Z"}""")
}
