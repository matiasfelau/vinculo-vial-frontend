package ar.felaufernandezbottaro.vinculovial.data.vehicle

import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationTokenGenerator
import ar.felaufernandezbottaro.vinculovial.data.crypto.MockAttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.LinkVehicleRequestDto
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
 * Test de contrato contra el Gateway simulado para `/vehicles`: sólo la forma de la
 * request. El mapeo de códigos de respuesta a resultado de dominio vive en
 * `VehicleRepositoryImpl`, no acá.
 */
class VehicleApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: VehicleApi

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
            .create(VehicleApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `publica en el endpoint del contrato con el bearer token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        api.linkVehicle("Bearer access-abc", LinkVehicleRequestDto("dni-enc", "pat-enc"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/vehicles", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
    }

    @Test
    fun `el cuerpo lleva dni_enc y pat_enc sin cruzar`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        api.linkVehicle("Bearer access-abc", LinkVehicleRequestDto("cryptograma-dni", "cryptograma-patente"))

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(2, body.length())
        assertEquals("cryptograma-dni", body.getString("dni_enc"))
        assertEquals("cryptograma-patente", body.getString("pat_enc"))
    }

    @Test
    fun `201 no trae cuerpo que parsear`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        val response = api.linkVehicle("Bearer access-abc", LinkVehicleRequestDto("a", "b"))

        assertEquals(201, response.code())
    }

    @Test
    fun `403 no trae cuerpo que parsear`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        val response = api.linkVehicle("Bearer access-abc", LinkVehicleRequestDto("a", "b"))

        assertEquals(403, response.code())
    }
}
