package ar.felaufernandezbottaro.vinculovial.data.inbox

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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Test de contrato contra el Gateway simulado para `/alerts/inbox`: sólo la forma de la
 * request y el parseo de la respuesta. El mapeo a resultado de dominio vive en
 * `InboxRepositoryImpl`, no acá.
 */
class InboxApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: InboxApi

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
            .create(InboxApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `consulta el endpoint del contrato con el bearer token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[],"next_cursor":null}"""))

        api.getInbox("Bearer access-abc")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/alerts/inbox", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
    }

    @Test
    fun `sin cursor no agrega query params`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[],"next_cursor":null}"""))

        api.getInbox("Bearer access-abc")

        assertEquals("/api/v1/alerts/inbox", server.takeRequest().path)
    }

    @Test
    fun `con cursor lo reenvia tal cual, sin interpretarlo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[],"next_cursor":null}"""))

        api.getInbox("Bearer access-abc", cursor = "cursor-opaco-b64")

        assertEquals("/api/v1/alerts/inbox?cursor=cursor-opaco-b64", server.takeRequest().path)
    }

    @Test
    fun `parsea items y next_cursor de una pagina con contenido`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "items": [
                    {"id": "uuid-1", "alert_type": "LUCES_ENCENDIDAS", "created_at": "2026-08-18T10:00:00Z"}
                  ],
                  "next_cursor": "siguiente-cursor"
                }
                """.trimIndent(),
            ),
        )

        val body = api.getInbox("Bearer access-abc").body()!!

        assertEquals(1, body.items.size)
        assertEquals("uuid-1", body.items[0].id)
        assertEquals("LUCES_ENCENDIDAS", body.items[0].alertType)
        assertEquals("2026-08-18T10:00:00Z", body.items[0].createdAt)
        assertEquals("siguiente-cursor", body.nextCursor)
    }

    @Test
    fun `una bandeja vacia es 200 con items vacio y next_cursor null, no un 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[],"next_cursor":null}"""))

        val response = api.getInbox("Bearer access-abc")

        assertEquals(200, response.code())
        assertEquals(emptyList<Any>(), response.body()!!.items)
        assertNull(response.body()!!.nextCursor)
    }

    @Test
    fun `429 no trae cuerpo que parsear`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        val response = api.getInbox("Bearer access-abc")

        assertEquals(429, response.code())
    }
}
