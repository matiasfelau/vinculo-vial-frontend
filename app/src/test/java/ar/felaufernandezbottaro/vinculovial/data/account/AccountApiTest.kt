package ar.felaufernandezbottaro.vinculovial.data.account

import ar.felaufernandezbottaro.vinculovial.data.account.dto.DeleteAccountRequestDto
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationUnavailableException
import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Test de contrato contra el Gateway simulado para `DELETE /api/v1/users/me` (US-11): la forma
 * de la request. El mapeo de respuestas a resultado de dominio vive en `AccountRepositoryImpl`.
 */
class AccountApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AccountApi

    /** Una attestation que siempre falla: esta ruta no la exige, y la request tiene que salir igual. */
    private val brokenAttestation = object : AttestationProvider {
        override suspend fun tokenFor(requestBody: ByteArray): String =
            throw AttestationUnavailableException("no disponible", AttestationUnavailableException.Reason.TRANSIENT)
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder()
            .addInterceptor(AttestationInterceptor(brokenAttestation))
            .build()

        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(AccountApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `es un DELETE a users me con el bearer token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        api.deleteAccount("Bearer access-abc", DeleteAccountRequestDto("mi-clave"))

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/users/me", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
    }

    @Test
    fun `el body es exactamente password`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        api.deleteAccount("Bearer access-abc", DeleteAccountRequestDto("mi-clave"))

        val request = server.takeRequest()
        assertEquals("""{"password":"mi-clave"}""", request.body.readUtf8())
        assertEquals("application/json; charset=UTF-8", request.getHeader("Content-Type"))
    }

    @Test
    fun `sin app-attestation la request sale igual y sin el header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = api.deleteAccount("Bearer access-abc", DeleteAccountRequestDto("mi-clave"))

        assertTrue(response.isSuccessful)
        assertEquals(204, response.code())
        assertNull(server.takeRequest().getHeader(AttestationInterceptor.HEADER))
    }

    @Test
    fun `el 401 INVALID_PASSWORD llega con su code legible`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":"contraseña incorrecta","code":"INVALID_PASSWORD"}"""),
        )

        val response = api.deleteAccount("Bearer access-abc", DeleteAccountRequestDto("mala"))

        assertFalse(response.isSuccessful)
        assertEquals(401, response.code())
        assertTrue(response.errorBody()?.string().orEmpty().contains("INVALID_PASSWORD"))
    }

    @Test
    fun `la contrasena no aparece en el toString del DTO`() {
        assertFalse(DeleteAccountRequestDto("mi-clave-secreta").toString().contains("mi-clave-secreta"))
    }
}
