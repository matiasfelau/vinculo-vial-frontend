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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Test de contrato contra el Gateway simulado para `/alerts/attempts`: sólo la forma de la
 * request y de la respuesta 201. El mapeo de códigos de respuesta a resultado de dominio vive
 * en `AlertAttemptRepositoryImpl`, no acá.
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

        api.startAttempt("Bearer access-abc", AlertAttemptRequestDto("cryptograma"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/alerts/attempts", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
    }

    @Test
    fun `el cuerpo lleva un unico pat_enc`() = runTest {
        server.enqueue(createdResponse())

        api.startAttempt("Bearer access-abc", AlertAttemptRequestDto("cryptograma-de-la-patente"))

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(1, body.length())
        assertEquals("cryptograma-de-la-patente", body.getString("pat_enc"))
    }

    @Test
    fun `201 trae attempt_id y expires_at`() = runTest {
        server.enqueue(createdResponse())

        val response = api.startAttempt("Bearer access-abc", AlertAttemptRequestDto("cryptograma"))

        assertEquals(201, response.code())
        assertEquals("3f2b8c1e-0000-4000-8000-000000000001", response.body()?.attemptId)
        assertEquals("2030-01-01T00:10:00Z", response.body()?.expiresAt)
    }

    private fun createdResponse() = MockResponse()
        .setResponseCode(201)
        .setBody("""{"attempt_id":"3f2b8c1e-0000-4000-8000-000000000001","expires_at":"2030-01-01T00:10:00Z"}""")
}
