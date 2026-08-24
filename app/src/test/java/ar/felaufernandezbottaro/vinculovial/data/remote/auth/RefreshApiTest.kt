package ar.felaufernandezbottaro.vinculovial.data.remote.auth

import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationTokenGenerator
import ar.felaufernandezbottaro.vinculovial.data.crypto.MockAttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshTokenRequestDto
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Test de contrato contra el Gateway simulado para `/refresh`: sólo la forma de la request y
 * de la respuesta. El mapeo de códigos de error a resultado de dominio vive en
 * `SessionManager` (feature/session-manager-mutex), no acá.
 */
class RefreshApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: RefreshApi

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
            .create(RefreshApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `publica en el endpoint del contrato con el header de attestation`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenBody()))

        api.refresh(RefreshTokenRequestDto("un-refresh-token"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/refresh", request.path)
        assertNotNull(
            "falta el header de attestation",
            request.getHeader(AttestationInterceptor.HEADER),
        )
    }

    @Test
    fun `el cuerpo es el refresh token en json plano, sin wrapper hpke`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenBody()))

        api.refresh(RefreshTokenRequestDto("un-refresh-token"))

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(1, body.length())
        assertEquals("un-refresh-token", body.getString("refresh_token"))
    }

    @Test
    fun `el 200 deserializa el par nuevo de tokens`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenBody(access = "access-nuevo", refresh = "refresh-nuevo")))

        val response = api.refresh(RefreshTokenRequestDto("un-refresh-token"))

        assertEquals(200, response.code())
        assertEquals("access-nuevo", response.body()?.accessToken)
        assertEquals("refresh-nuevo", response.body()?.refreshToken)
    }

    @Test
    fun `401 no trae cuerpo que parsear`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val response = api.refresh(RefreshTokenRequestDto("un-refresh-token"))

        assertEquals(401, response.code())
    }

    @Test
    fun `403 no trae cuerpo que parsear`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        val response = api.refresh(RefreshTokenRequestDto("un-refresh-token"))

        assertEquals(403, response.code())
    }

    private fun tokenBody(access: String = "access-abc", refresh: String = "refresh-xyz") =
        """{"access_token":"$access","refresh_token":"$refresh"}"""

    @Test
    fun `logout publica en el endpoint del contrato`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        api.logout(RefreshTokenRequestDto("un-refresh-token"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/logout", request.path)
    }

    @Test
    fun `logout manda el mismo payload plano que refresh`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        api.logout(RefreshTokenRequestDto("un-refresh-token"))

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(1, body.length())
        assertEquals("un-refresh-token", body.getString("refresh_token"))
    }
}
