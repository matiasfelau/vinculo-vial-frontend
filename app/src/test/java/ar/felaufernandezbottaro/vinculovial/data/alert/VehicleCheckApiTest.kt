package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.VehicleCheckRequestDto
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
 * Test de contrato contra el Gateway simulado para `/vehicles/check`: sólo la forma de la
 * request. El mapeo de códigos de respuesta a resultado de dominio vive en
 * `VehicleCheckRepositoryImpl`, no acá.
 */
class VehicleCheckApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: VehicleCheckApi

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
            .create(VehicleCheckApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `publica en el endpoint del contrato con el bearer token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        api.checkVehicle("Bearer access-abc", VehicleCheckRequestDto("cryptograma"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/vehicles/check", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
    }

    @Test
    fun `el cuerpo lleva un unico pat_enc`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        api.checkVehicle("Bearer access-abc", VehicleCheckRequestDto("cryptograma-de-la-patente"))

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(1, body.length())
        assertEquals("cryptograma-de-la-patente", body.getString("pat_enc"))
    }

    @Test
    fun `200 no trae cuerpo que parsear`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val response = api.checkVehicle("Bearer access-abc", VehicleCheckRequestDto("cryptograma"))

        assertEquals(200, response.code())
    }

    @Test
    fun `404 no trae cuerpo que parsear`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val response = api.checkVehicle("Bearer access-abc", VehicleCheckRequestDto("cryptograma"))

        assertEquals(404, response.code())
    }
}
