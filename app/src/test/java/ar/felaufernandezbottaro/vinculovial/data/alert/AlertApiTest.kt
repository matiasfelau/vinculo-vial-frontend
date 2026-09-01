package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.SendAlertRequestDto
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
 * Test de contrato contra el Gateway simulado para `/alerts`: sólo la forma de la request. El
 * mapeo de códigos de respuesta a resultado de dominio vive en `AlertRepositoryImpl`, no acá.
 */
class AlertApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AlertApi

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
            .create(AlertApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `publica en el endpoint del contrato con el bearer token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        api.sendAlert("Bearer access-abc", SendAlertRequestDto(alertType = "Chocado", patEnc = "cryptograma"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/alerts", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
    }

    @Test
    fun `el cuerpo lleva alert_type y pat_enc`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        api.sendAlert(
            "Bearer access-abc",
            SendAlertRequestDto(alertType = "Chocado", patEnc = "cryptograma-de-la-patente"),
        )

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(2, body.length())
        assertEquals("Chocado", body.getString("alert_type"))
        assertEquals("cryptograma-de-la-patente", body.getString("pat_enc"))
    }

    @Test
    fun `202 no trae cuerpo que parsear`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        val response = api.sendAlert("Bearer access-abc", SendAlertRequestDto("Chocado", "cryptograma"))

        assertEquals(202, response.code())
    }

    @Test
    fun `400 no trae cuerpo que parsear`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))

        val response = api.sendAlert("Bearer access-abc", SendAlertRequestDto("Chocado", "cryptograma"))

        assertEquals(400, response.code())
    }
}
